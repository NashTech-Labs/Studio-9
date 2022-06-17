package argo.service.config

import akka.actor.{ ActorRef, Props }
import akka.pattern.{ ask, pipe }
import akka.util.Timeout
import argo.common.elastic.ElasticSearchRepository.DeleteResult
import argo.common.service.{ DateSupport, NamedActor, Service, UUIDSupport }
import argo.domain.service.config.{ ConfigSetting, CreateUpdateConfigSetting, DeleteConfigSetting, RetrieveConfigSetting }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object ConfigSettingCommandService extends NamedActor {
  override val Name = "config-setting-command-service"
  def props(configSettingQueryService: ActorRef): Props = {
    Props(new ConfigSettingCommandService(configSettingQueryService))
  }
}

class ConfigSettingCommandService(configSettingQueryService: ActorRef) extends Service with UUIDSupport with DateSupport {

  log.debug(s"ConfigSettingCommandService is starting at {}", self.path)

  implicit val ec = context.dispatcher
  implicit val timeout = Timeout(30 seconds) // TODO: move this to settings

  val repository = ConfigSettingRepository(context.system)

  def receive: Receive = {

    case CreateUpdateConfigSetting(serviceName, settingName, settingValue, tags) => {
      def create(): Future[ConfigSetting] = {
        log.info("[ ConfigSetting: [{}/{}] ] - [Create/Update ConfigSetting] - Creating Config Setting in ElasticSearch.", serviceName, settingName)

        val id = randomUUID()
        val createDate = currentDate()

        val configSetting = ConfigSetting(
          id            = id,
          service_name  = serviceName,
          setting_name  = settingName,
          setting_value = settingValue,
          tags          = tags,
          created_at    = createDate,
          updated_at    = createDate
        )

        repository.create(configSetting)
      }

      def update(configSetting: ConfigSetting): Future[ConfigSetting] = {
        log.info("[ ConfigSetting: [{}/{}] ] - [Create/Update ConfigSetting] - Updating Config Setting in ElasticSearch.", serviceName, settingName)

        val updatedEntity =
          configSetting.copy(
            setting_value = settingValue,
            tags          = tags,
            updated_at    = currentDate()
          )

        repository.update(configSetting.id, updatedEntity) flatMap {
          case Some(configSetting) => Future.successful(configSetting)
          case None                => Future.failed(new Exception("Try to update an existent ConfigSetting but it was not found"))
        }
      }

      log.info("[ ConfigSetting: [{}/{}] ] - [Create/Update ConfigSetting] - Creating/Updating Config Setting in ElasticSearch.", serviceName, settingName)

      val result =
        findConfigSetting(serviceName, settingName) flatMap {
          case Some(configSetting) => update(configSetting)
          case None                => create()
        }

      result onComplete {
        case Success(_) => log.info("[ ConfigSetting: [{}/{}] ] - [Create/Update ConfigSetting] - Config Setting creation/update succeeded.", serviceName, settingName)
        case Failure(e) => log.error("[ ConfigSetting: [{}/{}] ] - [Create/Update ConfigSetting] - Failed to create/update Config Setting with error: [{}]", serviceName, settingName, e)
      }

      result pipeTo sender
    }

    case DeleteConfigSetting(serviceName, settingName) => {
      def delete(configSetting: ConfigSetting): Future[Option[ConfigSetting]] = {
        repository.delete(configSetting.id) flatMap {
          case DeleteResult(true) => Future.successful(Some(configSetting))
          case DeleteResult(_)    => Future.successful(None)
        }
      }

      log.info("[ ConfigSetting: [{}/{}] ] - [Delete ConfigSetting] - Config Setting from ElasticSearch.", serviceName, settingName)

      val result =
        findConfigSetting(serviceName, settingName) flatMap {
          case Some(configSetting) => delete(configSetting)
          case None                => Future.successful(None)
        }

      result onComplete {
        case Success(_) => log.info("[ ConfigSetting: [{}/{}] ] - [ConfigSetting] - Config Setting deletion succeeded.", serviceName, settingName)
        case Failure(e) => log.error("[ ConfigSetting: [{}/{}] ] - [ConfigSetting] - Failed to delete Config Setting with error: {}", serviceName, settingName, e)
      }

      result pipeTo sender
    }
  }

  def findConfigSetting(serviceName: String, settingName: String): Future[Option[ConfigSetting]] = {
    (configSettingQueryService ? RetrieveConfigSetting(serviceName, settingName)).mapTo[Option[ConfigSetting]]
  }

}

