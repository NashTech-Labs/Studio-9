package taurus.baile

import java.util.UUID

import akka.actor.Status
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import akka.testkit.TestActorRef
import cortex.api.baile.{ PredictionResultItem, SavePredictionResultRequest }
import taurus.baile.BaileService.SavePredictionResult
import taurus.common.json4s.TaurusJson4sSupport
import taurus.testkit.service.ServiceBaseSpec

import scala.concurrent.Future

class BaileServiceSpec extends ServiceBaseSpec {

  val albumId = UUID.randomUUID().toString

  val saveResultMessage = SavePredictionResult(
    jobId   = UUID.randomUUID(),
    request = SavePredictionResultRequest(
      albumId = albumId,
      results = Seq(
        PredictionResultItem(
          filePath   = "images/file1.png",
          fileSize   = 32L,
          fileName   = "tank.png",
          metadata   = Map.empty,
          label      = "tank",
          confidence = 1
        ),
        PredictionResultItem(
          filePath   = "images/file2.png",
          fileSize   = 44L,
          fileName   = "bus.png",
          metadata   = Map.empty,
          label      = "tank",
          confidence = 1
        )
      )
    )
  )

  val mockedBaileBaseUrl = "http://0.0.0.0:9000/v1"
  val saveResultURL = s"$mockedBaileBaseUrl/internal/cv-online-prediction/$albumId"
  val mockedBaileCredentials = BasicHttpCredentials("baile-username", "baile-password")
  val mockedBaileRetryCount = 1

  trait Scope extends ServiceScope with TaurusJson4sSupport {
    val service = TestActorRef(new BaileService with HttpClientSupportTesting {
      override val baileBaseUrl = mockedBaileBaseUrl
      override val baileCredentials = mockedBaileCredentials
      override val baileRequestRetryCount = mockedBaileRetryCount
    })
  }

  "When receiving a SavePredictionResult, BaileService" should {
    "send a request with job result to Baile API and respond with success" in new Scope {
      // Mock Http call
      mockHttpClient
        .putExpects(saveResultURL, saveResultMessage.request, Some(mockedBaileCredentials))
        .returning(Future.successful(HttpResponse(status = StatusCodes.OK)))

      watch(service)
      service ! saveResultMessage
      expectMsg(())
    }
    "log an error if sending a request to Baile API fails and respond with failure" in new Scope {
      // Mock Http call
      mockHttpClient
        .putExpects(saveResultURL, saveResultMessage.request, Some(mockedBaileCredentials))
        .returning(Future.successful(HttpResponse(status = StatusCodes.InternalServerError)))

      watch(service)
      service ! saveResultMessage
      expectMsgType[Status.Failure]
    }
  }
}
