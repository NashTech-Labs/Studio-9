package argo.rest.config

import akka.actor.ActorRef
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import argo.common.json4s.ArgoJson4sSupport
import argo.common.rest.BaseConfig
import argo.common.rest.routes.{ CRUDRoutes, HttpEndpoint, Translator }
import argo.domain.rest.config.{ ConfigSettingContract, CreateUpdateConfigSettingContract }
import argo.domain.service.config._

object ConfigSettingHttpEndpoint {

  def apply(configSettingCommandService: ActorRef, configSettingQueryService: ActorRef, config: BaseConfig): ConfigSettingHttpEndpoint = {
    new ConfigSettingHttpEndpoint(configSettingCommandService: ActorRef, configSettingQueryService: ActorRef, config: BaseConfig)
  }

  implicit val toConfigSettingContract = new Translator[ConfigSetting, ConfigSettingContract] {
    def translate(from: ConfigSetting): ConfigSettingContract = {
      ConfigSettingContract(
        serviceName  = from.service_name,
        settingName  = from.setting_name,
        settingValue = from.setting_value,
        tags         = from.tags,
        createdAt    = from.created_at,
        updatedAt    = from.updated_at
      )
    }
  }

}

class ConfigSettingHttpEndpoint(configSettingCommandService: ActorRef, configSettingQueryService: ActorRef, val config: BaseConfig) extends HttpEndpoint
    with ArgoJson4sSupport
    with CRUDRoutes {

  import ConfigSettingHttpEndpoint._

  def createUpdate(serviceName: String): Route = {
    (path(Segment) & put & entity(as[CreateUpdateConfigSettingContract])) { (settingName, createUpdateContract) =>
      val msg = CreateUpdateConfigSetting(serviceName, settingName, createUpdateContract.settingValue, createUpdateContract.tags)
      onSuccess((configSettingCommandService ? msg).mapTo[ConfigSetting]) { configSetting =>
        respond(configSetting)
      }
    }
  }

  def retrieve(serviceName: String): Route = {
    (path(Segment) & get) { settingName =>
      onSuccess((configSettingQueryService ? RetrieveConfigSetting(serviceName, settingName)).mapTo[Option[ConfigSetting]]) {
        case Some(configSetting) => respond(configSetting)
        case None                => respond(StatusCodes.NotFound)
      }
    }
  }

  def remove(serviceName: String): Route = {
    (path(Segment) & delete) { settingName =>
      onSuccess((configSettingCommandService ? DeleteConfigSetting(serviceName, settingName)).mapTo[Option[ConfigSetting]]) {
        case Some(configSetting) => respond(configSetting)
        case None                => respond(StatusCodes.NotFound)
      }
    }
  }

  def list(serviceName: String): Route = {
    (pathEnd & get) {
      onSuccess((configSettingQueryService ? RetrieveConfigSettings(serviceName)).mapTo[Seq[ConfigSetting]]) { configSettings =>
        respond(configSettings)
      }
    }
  }

  val routes: Route =
    pathPrefix("services" / Segment / "config-settings") { serviceName =>
      createUpdate(serviceName) ~
        retrieve(serviceName) ~
        remove(serviceName) ~
        list(serviceName)
    }

}