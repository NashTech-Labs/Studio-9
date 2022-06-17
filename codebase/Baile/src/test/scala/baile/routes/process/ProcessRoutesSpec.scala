package baile.routes.process

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import baile.domain.process.ProcessStatus
import baile.domain.usermanagement.User
import baile.services.process.util.TestData._
import baile.routes.RoutesSpec
import baile.services.common.AuthenticationService
import baile.services.process.ProcessService
import baile.services.process.ProcessService.{ ActionForbiddenError, ProcessNotFoundError }
import baile.services.table.TableUploadResultHandler
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import play.api.libs.json.{ JsNumber, JsObject, JsString }
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._

class ProcessRoutesSpec extends RoutesSpec {

  val sampleProcess = SampleProcess.copy(entity = SampleProcess.entity.copy(
    status = randomOf(
      ProcessStatus.Queued,
      ProcessStatus.Running,
      ProcessStatus.Completed,
      ProcessStatus.Cancelled,
      ProcessStatus.Failed
    )
  ))
  val sampleProcessEntity = sampleProcess.entity

  private val service = mock[ProcessService]
  private val authenticationService = mock[AuthenticationService]

  when(authenticationService.authenticate(userToken)).thenReturn(future(Some(SampleUser)))

  val routes: Route = new ProcessRoutes(conf, service, authenticationService).routes

  "GET /processes/:id" should {

    "return 200 with process response" in {
      when(service.getProcess(anyString)).thenReturn(future(sampleProcess.asRight))

      Get("/processes/42").signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        validateResponse(response)
      }
    }

    "return 404" in {
      when(service.getProcess(anyString)).thenReturn(future(ProcessNotFoundError.asLeft))

      Get("/processes/42").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }

  "POST /processes/:id/cancel" should {

    "return 200 with process id" in {
      when(service.cancelProcess(anyString)(any[User])).thenReturn(future(().asRight))

      Post("/processes/42/cancel").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("42")))
      }
    }

    "return 404" in {
      when(service.cancelProcess(anyString)(any[User])).thenReturn(future(ActionForbiddenError.asLeft))

      Post("/processes/42/cancel").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }

  "GET /processes/" should {

    "return success response from get processes" in {
      val dateTime = Instant.now()
      when(service.list(
        Seq(),
        1,
        2,
        Some(Seq(classOf[TableUploadResultHandler].getCanonicalName)),
        Some(Seq(dateTime, dateTime)),
        Some(Seq(dateTime, dateTime))
      )(SampleUser)) thenReturn future(Right((Seq(sampleProcess), 1)))
      Get(s"/processes?page=1&page_size=2&jobTypes=TABULAR_UPLOAD&processStarted=" +
        s"${dateTime.toString()},${dateTime.toString()}&processCompleted=" +
        s"${dateTime.toString()},${dateTime.toString()}"
      )
        .signed.check {
        status shouldBe StatusCodes.OK
      }
    }

    "return error response from get processes" in {
      when(service.list(Seq(), 1, 2, None, None, None)(SampleUser)) thenReturn
        future(Left(ActionForbiddenError))
      Get("/processes?page=1&page_size=2").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  private def validateResponse(response: JsObject) = {
    response.fields should contain allOf(
      "target" -> JsString(TargetType.toString.toUpperCase),
      "targetId" -> JsString(TargetId),
      "progress" -> JsNumber(sampleProcessEntity.progress.get),
      "status" -> JsString(sampleProcessEntity.status.toString.toUpperCase)
    )
    Instant.parse((response \ "created").as[String]) shouldBe sampleProcessEntity.created
  }

}
