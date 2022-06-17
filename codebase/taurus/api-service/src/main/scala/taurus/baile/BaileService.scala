package taurus.baile

import java.util.UUID

import akka.actor.Props
import akka.pattern.pipe
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import akka.util.Timeout
import cortex.api.baile.SavePredictionResultRequest
import orion.ipc.common.withRetry
import taurus.baile.BaileService.SavePredictionResult
import taurus.common.json4s.TaurusJson4sSupport
import taurus.common.service.{ HttpClientSupport, Service }

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object BaileService {

  def props(): Props = {
    Props(new BaileService)
  }

  case class SavePredictionResult(jobId: UUID, request: SavePredictionResultRequest)

}

class BaileService extends Service with HttpClientSupport with TaurusJson4sSupport {

  implicit val ec: ExecutionContextExecutor = context.dispatcher
  implicit val timeout: Timeout = Timeout(30.seconds)

  val baileSettings: BaileSettings = BaileSettings(context.system)
  val baileBaseUrl: String = baileSettings.baseUrl
  val baileCredentials: BasicHttpCredentials = BasicHttpCredentials(baileSettings.credentials.username, baileSettings.credentials.password)
  val baileRequestRetryCount: Int = baileSettings.requestRetryCount

  private def saveResultURL(streamId: String) = s"$baileBaseUrl/internal/cv-online-prediction/$streamId"

  override def receive: Receive = {
    case msg @ SavePredictionResult(jobId, request) =>
      log.info("[ JobId: {} ] - [Save Job Result] - Starting saving Baile part.", jobId)

      val result =
        withRetry(baileRequestRetryCount)(httpClient.put(saveResultURL(request.albumId), request, Some(baileCredentials))) map {
          case HttpResponse(StatusCodes.OK, _, _, _) => ()
          case other                                 => throw new Exception(s"Unexpected response [$other]")
        }

      result onComplete {
        case Failure(e) =>
          log.error("[ JobId: {} ] - [Save Job Result] - Failed to save Baile part [{}] with error: [{}].", jobId, msg, e)
        case Success(_) =>
          log.info("[ JobId: {} ] - [Save Job Result] - Baile part saving completed.", jobId)
      }

      result pipeTo sender
  }

}
