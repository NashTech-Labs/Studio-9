package baile.routes.dcproject

import java.time.Instant

import akka.NotUsed
import akka.http.scaladsl.model.{ ContentType, MediaTypes, StatusCodes }
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{ Sink, Source }
import baile.daocommons.WithId
import baile.domain.dcproject.{ Session, SessionStatus }
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.services.common.AuthenticationService
import baile.services.dcproject.SessionService
import baile.services.dcproject.SessionService.SessionServiceError._
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.mockito.ArgumentMatchers.{ eq => eqTo }
import org.mockito.Mockito.when
import play.api.libs.json.{ JsObject, JsString, Json }

class SessionRoutesSpec extends RoutesSpec {

  val dateTime = Instant.now()

  val sessionEntity = WithId(
    Session(
      geminiSessionId = randomString(),
      geminiSessionToken = randomString(),
      geminiSessionUrl = randomString(),
      dcProjectId = randomString(),
      created = dateTime
    ),
    randomString()
  )

  val projectId = randomString()

  implicit val user: User = SampleUser

  private val service = mock[SessionService]
  private val authenticationService = mock[AuthenticationService]

  when(authenticationService.authenticate(eqTo(userToken))).thenReturn(future(Some(user)))

  val routes: Route = new SessionRoutes(conf, authenticationService, service).routes

  "GET /dc-projects/:id/session" should {

    "return 200 with server sent events body" in {
      val source = Source(List(
        SessionStatus.Submitted,
        SessionStatus.Running,
        SessionStatus.Queued
      ))
      when(service.getStatusesSource(eqTo(projectId))(eqTo(user))).thenReturn(future(source.asRight))

      Get(s"/dc-projects/$projectId/session/status").signed.check(ContentType(MediaTypes.`text/event-stream`)) {
        status shouldBe StatusCodes.OK

        import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._
        whenReady(responseAs[Source[ServerSentEvent, NotUsed]].runWith(Sink.seq[ServerSentEvent])) { result =>
          result.forall(_.eventType.isEmpty) shouldBe true
          result.map(_.data) shouldBe Seq(
            "SUBMITTED",
            "RUNNING",
            "QUEUED"
          )
        }
      }
    }

    "return error response" in {
      when(service.getStatusesSource(eqTo(projectId))(eqTo(user))).thenReturn(future(SessionNotFound.asLeft))

      Get(s"/dc-projects/$projectId/session/status").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }

  "POST /dc-projects/:id/session" should {

    "return 200 with session information response" in {
      when(service.create(
        eqTo(projectId),
        eqTo(userToken),
        eqTo(true)
      )(eqTo(user))).thenReturn(future(sessionEntity.asRight))

      Post(s"/dc-projects/$projectId/session", Json.parse("""{"useGPU":true}""")).signed.check {
        status shouldBe StatusCodes.OK
        val response = responseAs[JsObject]
        response.fields should contain allOf(
          "id" -> JsString(sessionEntity.entity.geminiSessionId),
          "authToken" -> JsString(sessionEntity.entity.geminiSessionToken),
          "url" -> JsString(sessionEntity.entity.geminiSessionUrl),
          "created" -> JsString(sessionEntity.entity.created.toString),
          "dcProjectId" -> JsString(sessionEntity.entity.dcProjectId)
        )
      }
    }

    "return error response" in {
      when(service.create(
        eqTo(projectId),
        eqTo(userToken),
        eqTo(true)
      )(eqTo(user))).thenReturn(future(ProjectAlreadyInSession.asLeft))

      Post(s"/dc-projects/$projectId/session", Json.parse("""{"useGPU":true}""")).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }

  "DELETE /dc-projects/:id/session" should {

    "return 200 with project id" in {
      when(service.cancel(eqTo(projectId))(eqTo(user))).thenReturn(future(().asRight))

      Delete(s"/dc-projects/$projectId/session").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString(projectId)))
      }
    }

    "return error response" in {
      when(service.cancel(eqTo(projectId))(eqTo(user))).thenReturn(future(DCProjectNotFound.asLeft))

      Delete(s"/dc-projects/$projectId/session").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

  }

}
