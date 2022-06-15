package baile.services.cortex.job

import java.time.Instant
import java.util.UUID

import akka.event.LoggingAdapter
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model._
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

class CortexServiceSpec extends BaseSpec {

  val cortexService = new CortexService(conf, httpMock)

  val jobId = UUID.randomUUID
  val ownerId = UUID.randomUUID

  val jobCreateRequest = CortexJobCreateRequest(
    id = jobId,
    owner = ownerId,
    jobType = "TRAIN",
    inputPath = "foo/bar"
  )

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

  "CortexService#createJob" should {

    "create new job" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.Created,
        entity = httpEntity(rawJobResponse)
      )))

      whenReady(cortexService.createJob(jobCreateRequest)) { response =>
        response.id shouldBe jobId
        response.owner shouldBe jobCreateRequest.owner
        response.jobType shouldBe jobCreateRequest.jobType
        response.inputPath shouldBe jobCreateRequest.inputPath
      }
    }

    "fail to create new job (bad http status code)" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.BadRequest)))

      whenReady(cortexService.createJob(jobCreateRequest).failed)(_ shouldBe a [UnexpectedResponseException])
    }

    "fail to create new job (fail to parse body)" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.Created,
        entity = httpEntity(JsObject(Seq("foo" -> JsString("bar"))))
      )))

      whenReady(cortexService.createJob(jobCreateRequest).failed)(_ shouldBe a [RejectionError])
    }

  }

  val jobStatusResponse = CortexJobStatusResponse(
    status = CortexJobStatus.Running,
    currentProgress = Some(0.2),
    estimatedTimeRemaining = None,
    cortexErrorDetails = None
  )

  val rawJobStatusResponse = JsObject(Seq(
    "status" -> Json.toJson(jobStatusResponse.status),
    "currentProgress" -> jobStatusResponse.currentProgress.fold[JsValue](JsNull)(JsNumber(_))
  ))

  "CortexService#getJobStatus" should {

    "return job status" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.OK,
        entity = httpEntity(rawJobStatusResponse)
      )))

      whenReady(cortexService.getJobStatus(jobId)) { response =>
        response.status shouldBe jobStatusResponse.status
        response.currentProgress shouldBe jobStatusResponse.currentProgress
        response.estimatedTimeRemaining shouldBe jobStatusResponse.estimatedTimeRemaining
      }
    }

    "fail to return job status (bad http status code)" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.BadRequest)))

      whenReady(cortexService.getJobStatus(jobId).failed)(_ shouldBe a [UnexpectedResponseException])
    }

    "fail to return job status (fail to parse body)" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.OK,
        entity = httpEntity(JsObject(Seq("foo" -> JsString("bar"))))
      )))

      whenReady(cortexService.getJobStatus(jobId).failed)(_ shouldBe a [RejectionError])
    }

  }

  "CortexService#cancelJob" should {

    "cancel job" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.OK)))

      cortexService.cancelJob(jobId).futureValue
    }

    "fail to return job status (bad http status code)" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.BadRequest)))

      whenReady(cortexService.cancelJob(jobId).failed)(_ shouldBe a[UnexpectedResponseException])
    }

  }

}
