package baile.services.cortex.job

import java.util.UUID

import akka.actor.Scheduler
import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.HttpMethods.GET
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import baile.services.cortex.datacontract.CortexJobResponse
import baile.services.http.HttpClientService
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class AriesService(
  val conf: Config,
  val http: HttpExt
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer,
  val logger: LoggingAdapter,
  val scheduler: Scheduler
) extends HttpClientService with PlayJsonSupport {

  private val ariesApiVersion = conf.getString("aries.api-version")
  private val ariesUrl = s"${ conf.getString("aries.rest-url") }/$ariesApiVersion"
  private val ariesUser = conf.getString("aries.user")
  private val ariesPassword = conf.getString("aries.password")

  private[cortex] def getJob(id: UUID): Future[CortexJobResponse] = {
    val result = for {
      response <- makeHttpRequest(
        HttpRequest(GET, s"$ariesUrl/jobs/$id").addCredentials(BasicHttpCredentials(ariesUser, ariesPassword))
      )
      cortexJobResponse <- Unmarshal(response.entity).to[CortexJobResponse]
    } yield cortexJobResponse

    val step = "Retrieve Job"

    result andThen {
      case Success(job) =>
        JobLogging.info(id, s"Successfully retrieved job $job from Aries", step)
      case Failure(f) =>
        JobLogging.error(id, s"Failed to retrieve job from Aries with error $f", step)
    }
  }

}

