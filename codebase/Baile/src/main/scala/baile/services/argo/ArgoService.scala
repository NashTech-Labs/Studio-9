package baile.services.argo

import akka.actor.Scheduler
import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import baile.services.http.HttpClientService
import com.typesafe.config.Config
import cortex.api.argo.{ ConfigSetting, UpdateConfigSetting }
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.{ ExecutionContext, Future }
import scala.util._

class ArgoService(
  val conf: Config,
  val http: HttpExt
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer,
  val logger: LoggingAdapter,
  val scheduler: Scheduler
) extends HttpClientService with PlayJsonSupport {

  private val argoApiVersion = conf.getString("argo.api-version")
  private val argoUrl = s"${ conf.getString("argo.rest-url") }/$argoApiVersion"
  private val argoUser = conf.getString("argo.user")
  private val argoPassword = conf.getString("argo.password")

  def setConfigValue(
    serviceName: String,
    key: String,
    value: String,
    tags: List[String] = List.empty
  ): Future[ConfigSetting] = {
    val request = UpdateConfigSetting(value, tags)
    val result =
      for {
        entity <- Marshal(request).to[MessageEntity]
        response <- sendAuthorizedRequest(
          HttpRequest(POST, s"$argoUrl/services/$serviceName/config-settings/$key").withEntity(entity)
        )
        configSettingResponse <- Unmarshal(response.entity).to[ConfigSetting]
      } yield configSettingResponse

    val step = "Set Config Value"

    result andThen {
      case Success(configSetting) =>
        logger.info("Service Name: {} - {} - {}",
          serviceName, step, s"Successfully set config value $configSetting via Argo-REST")
      case Failure(f) =>
        logger.error("Service Name: {} - {} - {}",
          serviceName, step, s"Failed to set config value $request with error: $f")
    }
  }

  def getConfigValue(serviceName: String, key: String): Future[ConfigSetting] = {
    val result =
      for {
        response <- sendAuthorizedRequest(
          HttpRequest(GET, s"$argoUrl/services/$serviceName/config-settings/$key")
        )
        configSettingResponse <- Unmarshal(response.entity).to[ConfigSetting]
      } yield configSettingResponse

    val step = "Get Config Value"

    result andThen {
      case Success(configValue) =>
        logger.info("Service Name: {} - {} - {}",
          serviceName, s"Successfully get config value $configValue via Argo-REST", step)
      case Failure(f) =>
        logger.error("Service Name: {} - {} - {}",
          serviceName, s"Failed to get config value from Argo-REST with error: $f", step)
    }
  }

  def getConfigValues(serviceName: String): Future[List[ConfigSetting]] = {
    val result =
      for {
        response <- sendAuthorizedRequest(
          HttpRequest(GET, s"$argoUrl/services/$serviceName/config-settings")
        )
        configSettingResponse <- Unmarshal(response.entity).to[List[ConfigSetting]]
      } yield configSettingResponse

    val step = "Get Config Values"

    result andThen {
      case Success(configValue) =>
        logger.info("Service Name: {} - {} - {}",
          serviceName, s"Successfully get config values $configValue via Argo-REST", step)
      case Failure(f) =>
        logger.error("Service Name: {} - {} - {}",
          serviceName, s"Failed to get config values from Argo-REST with error: $f", step)
    }
  }

  private def sendAuthorizedRequest(baseRequest: HttpRequest, expectedCode: StatusCode = StatusCodes.OK)
  : Future[HttpResponse] =
    makeHttpRequest(
      baseRequest.addCredentials(BasicHttpCredentials(argoUser, argoPassword)),
      expectedCode = expectedCode
    )

  def deleteConfigValue(serviceName: String, key: String): Future[Unit] = {
    val result =
      sendAuthorizedRequest(HttpRequest(DELETE, s"$argoUrl/services/$serviceName/config-settings/$key")).map(_ => ())

    val step = "Delete Config Value"

    result andThen {
      case Success(_) =>
        logger.info("Service Name: {} - {} - {}",
          serviceName, "Successfully deleted config value via Argo-REST", step)
      case Failure(f) =>
        logger.error("Service Name: {} - {} - {}",
          serviceName, s"Failed to delete config value via Argo-REST: $f", step)
    }
  }
}
