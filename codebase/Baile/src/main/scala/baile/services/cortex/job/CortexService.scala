package baile.services.cortex.job

import java.util.UUID

import akka.actor.Scheduler
import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import baile.services.cortex.datacontract.{ CortexJobCreateRequest, CortexJobResponse, CortexJobStatusResponse }
import baile.services.http.HttpClientService
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class CortexService(
  val conf: Config,
  val http: HttpExt
)(
  implicit val ec: ExecutionContext,
  val materializer: Materializer,
  val logger: LoggingAdapter,
  val scheduler: Scheduler
) extends HttpClientService with PlayJsonSupport {

  private val cortexApiVersion = conf.getString("cortex.api-version")
  private val cortexUrl = s"${ conf.getString("cortex.rest-url") }/$cortexApiVersion"
  private val cortexUser = conf.getString("cortex.user")
  private val cortexPassword = conf.getString("cortex.password")

  private[cortex] def createJob(request: CortexJobCreateRequest): Future[CortexJobResponse] = {
    val result =
      for {
        entity <- Marshal(request).to[MessageEntity]
        response <- sendAuthorizedRequest(
          HttpRequest(POST, s"$cortexUrl/jobs").withEntity(entity),
          expectedCode = StatusCodes.Created
        )
        cortexJobResponse <- Unmarshal(response.entity).to[CortexJobResponse]
      } yield cortexJobResponse

    val step = "Create Job"

    result andThen {
      case Success(createdJob) =>
        JobLogging.info(request.id, s"Successfully created job $createdJob via Cortex-REST", step)
      case Failure(f) =>
        JobLogging.error(request.id, s"Failed to create job $request with error $f", step)
    }
  }

  private[cortex] def getJobStatus(id: UUID): Future[CortexJobStatusResponse] = {
    val result = for {
      response <- sendAuthorizedRequest(HttpRequest(GET, s"$cortexUrl/jobs/$id/status"))
      cortexJobStatusResponse <- Unmarshal(response.entity).to[CortexJobStatusResponse]
    } yield cortexJobStatusResponse

    val step = "Retrieve Job Status"

    result andThen {
      case Success(jobStatus) =>
        JobLogging.info(id, s"Successfully retrieved job status $jobStatus via Cortex-REST", step)
      case Failure(f) =>
        JobLogging.error(id, s"Failed to retrieve job status from Cortex-REST with error: $f", step)
    }
  }

  private[cortex] def cancelJob(id: UUID): Future[Unit] = {
    val result = sendAuthorizedRequest(HttpRequest(DELETE, s"$cortexUrl/jobs/$id")).map(_ => ())

    val step = "Cancel Job"

    result andThen {
      case Success(_) =>
        JobLogging.info(id, "Successfully canceled job via Cortex-REST", step)
      case Failure(f) =>
        JobLogging.error(id, s"Failed to cancel job via Cortex-REST with error: $f", step)
    }
  }

  private def sendAuthorizedRequest(
    baseRequest: HttpRequest,
    expectedCode: StatusCode = StatusCodes.OK
  ): Future[HttpResponse] =
    makeHttpRequest(
      baseRequest.addCredentials(BasicHttpCredentials(cortexUser, cortexPassword)),
      expectedCode = expectedCode
    )

}
