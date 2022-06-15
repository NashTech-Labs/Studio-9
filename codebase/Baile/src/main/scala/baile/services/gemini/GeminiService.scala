package baile.services.gemini

import akka.NotUsed
import akka.actor.{ ActorSystem, Scheduler }
import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import baile.services.http.HttpClientService
import com.typesafe.config.Config
import cortex.api.gemini.{ JupyterSessionRequest, JupyterSessionResponse, SessionStatus }
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import play.api.libs.json.Json

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class GeminiService(
  val conf: Config,
  val http: HttpExt
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer,
  val logger: LoggingAdapter,
  val scheduler: Scheduler,
  val system: ActorSystem
) extends HttpClientService with PlayJsonSupport {

  private val geminiApiVersion = conf.getString("gemini.api-version")
  private val geminiUrl = s"${ conf.getString("gemini.rest-url") }/$geminiApiVersion"
  private val geminiUser = conf.getString("gemini.user")
  private val geminiPassword = conf.getString("gemini.password")

  private[services] def createSession(request: JupyterSessionRequest): Future[JupyterSessionResponse] = {
    val result =
      for {
        entity <- Marshal(request).to[MessageEntity]
        response <- sendAuthorizedRequest(
          HttpRequest(POST, s"$geminiUrl/sessions").withEntity(entity),
          expectedCode = StatusCodes.Created
        )
        jupyterSessionResponse <- Unmarshal(response.entity).to[JupyterSessionResponse]
      } yield jupyterSessionResponse

    result andThen {
      case Success(createdSession) =>
        logger.info(s"Successfully created session $createdSession via gemini")
      case Failure(f) =>
        logger.error(s"Failed to create session $request with error $f")
    }
  }

  private[services] def getSession(id: String): Future[JupyterSessionResponse] = {
    val result = for {
      response <- sendAuthorizedRequest(HttpRequest(GET, s"$geminiUrl/sessions/$id"))
      jupyterSession <- Unmarshal(response.entity).to[JupyterSessionResponse]
    } yield jupyterSession

    result andThen {
      case Success(session) =>
        logger.info(s"Successfully retrieved session $session via gemini")
      case Failure(f) =>
        logger.error(s"Failed to retrieve session via gemini with error: $f")
    }
  }

  private[services] def getSessionStatusesSource(id: String): Future[Source[SessionStatus, NotUsed]] = {
    import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._

    val result = for {
      response <- sendAuthorizedRequest(HttpRequest(GET, s"$geminiUrl/sessions/$id/status"))
      sse <- Unmarshal(response.entity).to[Source[ServerSentEvent, NotUsed]]
    } yield sse.collect { case event if event.eventType.contains("Status") =>
      Json.fromJson[SessionStatus](Json.parse(event.data)).get
    }

    result andThen {
      case Success(source) =>
        logger.info(s"Successfully retrieved session statuses $source via gemini")
      case Failure(f) =>
        logger.error(s"Failed to retrieve session statuses via gemini with error: $f")
    }
  }

  private[services] def cancelSession(id: String): Future[Unit] = {
    val result = sendAuthorizedRequest(HttpRequest(DELETE, s"$geminiUrl/sessions/$id")).map(_ => ())

    result andThen {
      case Success(_) =>
        logger.info("Successfully canceled session via gemini")
      case Failure(f) =>
        logger.error(s"Failed to cancel session via gemini with error: $f")
    }
  }

  private def sendAuthorizedRequest(
    baseRequest: HttpRequest,
    expectedCode: StatusCode = StatusCodes.OK
  ): Future[HttpResponse] =
    makeHttpRequest(
      baseRequest.addCredentials(BasicHttpCredentials(geminiUser, geminiPassword)),
      expectedCode = expectedCode
    )

}
