package baile.services.cortex.job

import java.time.Instant
import java.util.UUID

import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import akka.http.scaladsl.server.RejectionError
import akka.http.scaladsl.settings.ConnectionPoolSettings
import baile.BaseSpec
import baile.domain.job.CortexJobStatus
import baile.services.cortex.datacontract._
import baile.services.http.exceptions.UnexpectedResponseException
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json._

import scala.concurrent.duration._

class AriesServiceSpec extends BaseSpec {

  val ariesService = new AriesService(conf, httpMock)

  val jobId: UUID = UUID.randomUUID
  val ownerId: UUID = UUID.randomUUID

  val cortexTimeInfo: CortexTimeInfoResponse = CortexTimeInfoResponse(
    submittedAt = Instant.now(),
    completedAt = Some(Instant.now()),
    startedAt = Some(Instant.now())
  )

  val jobResponse = CortexJobResponse(
    id = jobId,
    owner = ownerId,
    jobType = "TRAIN",
    status = CortexJobStatus.Queued,
    inputPath = "foo/bar",
    outputPath = None,
    timeInfo = cortexTimeInfo,
    tasksQueuedTime = Some(10.minutes),
    tasksTimeInfo = Seq.empty
  )

  val rawJobResponse: JsObject = Json.obj(
    "id" -> Json.toJson(jobResponse.id),
    "owner" -> Json.toJson(jobResponse.owner),
    "jobType" -> Json.toJson(jobResponse.jobType),
    "status" -> Json.toJson(jobResponse.status),
    "inputPath" -> JsString(jobResponse.inputPath),
    "tasksQueuedTime" -> Json.obj(
      "unit" -> JsString(jobResponse.tasksQueuedTime.get.unit.toString),
      "length" -> JsNumber(jobResponse.tasksQueuedTime.get.length)
    ),
    "timeInfo" -> Json.obj(
      "startedAt" -> Json.toJson(cortexTimeInfo.startedAt),
      "submittedAt" -> Json.toJson(cortexTimeInfo.submittedAt),
      "completedAt" -> Json.toJson(cortexTimeInfo.completedAt)
    ),
    "tasksTimeInfo" -> JsArray()
  )

  "AriesService#getJob" should {

    "successfully return job" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.OK,
        entity = httpEntity(rawJobResponse)
      )))

      whenReady(ariesService.getJob(jobId)) { response =>
        response shouldBe jobResponse
      }
    }

    "fail to return job (bad http status code)" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.NotFound)))

      whenReady(ariesService.getJob(jobId).failed)(_ shouldBe a [UnexpectedResponseException])
    }

    "fail to return job (fail to parse body)" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.OK,
        entity = httpEntity(JsObject(Seq("foo" -> JsString("bar"))))
      )))

      whenReady(ariesService.getJob(jobId).failed)(_ shouldBe a [RejectionError])
    }

  }

}
