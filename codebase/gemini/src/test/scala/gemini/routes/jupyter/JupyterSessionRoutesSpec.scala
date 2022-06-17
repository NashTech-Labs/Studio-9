package gemini.routes.jupyter

import java.time.Instant
import java.util.UUID

import akka.NotUsed
import akka.http.scaladsl.model.headers.{ BasicHttpCredentials, HttpCredentials, OAuth2BearerToken }
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{ Sink, Source }
import cortex.api.gemini.{ JupyterNodeParamsRequest, JupyterSessionRequest, JupyterSessionResponse, SessionStatus => ContractStatus }
import gemini.RandomGenerators._
import gemini.domain.jupyter.{ Session, SessionStatus }
import gemini.routes.RoutesSpec
import gemini.routes.jupyter.JupyterSessionRoutes.Settings
import gemini.services.jupyter.JupyterSessionService
import play.api.libs.json.Json
import resscheduler.ResourceProvider
import resscheduler.routes.ResourceRoutes

import scala.concurrent.Future

class JupyterSessionRoutesSpec extends RoutesSpec {

  trait Setup extends RoutesSetup {
    val userName = "john"
    val userPassword = "badluck"
    val bearerToken: String = randomString()
    val jupyterSessionService: JupyterSessionService = mock[JupyterSessionService]
    val resourceRoute = new ResourceRoutes(mock[ResourceProvider[Future]], _ => "").routes

    val routes: Route = new JupyterSessionRoutes(
      Settings(userName, userPassword),
      jupyterSessionService
    ).routes

    val session = Session(UUID.randomUUID(), "token", "http://deepcortex.ai/42/", SessionStatus.Submitted, Instant.now)

    implicit val creds: HttpCredentials = BasicHttpCredentials(userName, userPassword)

    def validateResponse(response: HttpResponse): Unit = {
      val jupyterSessionResponse = responseAs[JupyterSessionResponse]
      jupyterSessionResponse.id shouldBe session.id.toString
      jupyterSessionResponse.token shouldBe session.token
      jupyterSessionResponse.url shouldBe session.url
      jupyterSessionResponse.status shouldBe ContractStatus.Submitted
    }
  }

  "POST /sessions" should {
    val request = JupyterSessionRequest(
      userAccessToken = randomString(),
      awsRegion = "us-west-1",
      awsAccessKey = randomString(),
      awsSecretKey = randomString(),
      awsSessionToken = randomString(),
      bucketName = "my.bucket",
      projectPath = "project/files",
      nodeParams = JupyterNodeParamsRequest(Some(1), Some(2))
    )

    "return 201 with session" in new Setup {
      jupyterSessionService.create(*, *, *, *) shouldReturn future(session)
      Post("/sessions", Json.toJson(request)).signed.check {
        status shouldBe StatusCodes.Created
        validateResponse(response)
      }
    }

    "return 401 when credentials are wrong" in new Setup {
      implicit override val creds: HttpCredentials = BasicHttpCredentials("admin", "foobarbaz")

      Post("/sessions", Json.toJson(request)).signed.check {
        status shouldBe StatusCodes.Unauthorized
        validateErrorResponse(response)
      }
    }

  }

  "GET /sessions/:id/status" should {

    "return 200 with server sent events body" in new Setup {
      import SessionStatus._

      val source = Source(List(Submitted, Running, Queued))
      jupyterSessionService.getStatusesSource(session.id) shouldReturn future(Some(source))

      Get(s"/sessions/${session.id}/status").signed.check(MediaTypes.`text/event-stream`.toContentType) {
        import akka.http.scaladsl.unmarshalling.sse.EventStreamUnmarshalling._

        status shouldBe StatusCodes.OK
        whenReady(responseAs[Source[ServerSentEvent, NotUsed]].runWith(Sink.seq[ServerSentEvent])) { result =>
          import cortex.api.gemini.{ SessionStatus => ContractStatus }

          result.forall(_.eventType.contains("Status")) shouldBe true
          result.map(_.data) shouldBe Seq(
            ContractStatus.Submitted,
            ContractStatus.Running,
            ContractStatus.Queued
          ).map(Json.toJson(_).toString)
        }
      }
    }

    "return 404 when session was not found" in new Setup {
      jupyterSessionService.getStatusesSource(session.id) shouldReturn future(None)

      Get(s"/sessions/${session.id}/status").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(response)
      }
    }

  }

  "GET /sessions/:id" should {

    "return 200 with session" in new Setup {
      jupyterSessionService.get(session.id) shouldReturn future(Some(session))

      Get(s"/sessions/${session.id}").signed.check {
        status shouldBe StatusCodes.OK
        validateResponse(response)
      }
    }

    "return 404 when session was not found" in new Setup {
      jupyterSessionService.get(session.id) shouldReturn future(None)

      Get(s"/sessions/${session.id}").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(response)
      }
    }

  }

  "DELETE /sessions/:id" should {
    "return 200" in new Setup {
      jupyterSessionService.stop(session.id) isLenient ()

      Delete(s"/sessions/${session.id}").signed.check {
        status shouldBe StatusCodes.OK
      }
    }

    "return 200 for correct bearer auth" in new Setup {
      implicit override val creds: HttpCredentials = OAuth2BearerToken(bearerToken)
      jupyterSessionService.authenticate(session.id, bearerToken) shouldReturn future(true)
      jupyterSessionService.stop(session.id) isLenient ()

      Delete(s"/sessions/${session.id}").signed.check {
        status shouldBe StatusCodes.OK
      }
    }

    "return 401 for incorrect bearer auth" in new Setup {
      private val incorrectBearerToken = randomString()
      implicit override val creds: HttpCredentials = OAuth2BearerToken(incorrectBearerToken)
      jupyterSessionService.authenticate(session.id, incorrectBearerToken) shouldReturn future(false)
      jupyterSessionService.stop(session.id) isLenient ()

      Delete(s"/sessions/${session.id}").signed.check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return 401 for no auth" in new Setup {
      jupyterSessionService.stop(session.id) isLenient ()

      Delete(s"/sessions/${session.id}").check {
        status shouldBe StatusCodes.Unauthorized
      }
    }
  }

  "POST /sessions/:id" should {

    "return 200 when correct bearer token is provided" in new Setup {
      implicit override val creds: HttpCredentials = OAuth2BearerToken(bearerToken)
      jupyterSessionService.authenticate(session.id, bearerToken) shouldReturn future(true)
      jupyterSessionService.sendHeartbeat(session.id) isLenient ()
      Post(s"/sessions/${session.id}").signed.check {
        status shouldBe StatusCodes.OK
      }
    }

    "return 401 when incorrect bearer token is provided" in new Setup {
      private val incorrectBearerToken = randomString()
      implicit override val creds: HttpCredentials = OAuth2BearerToken(incorrectBearerToken)
      jupyterSessionService.authenticate(session.id, incorrectBearerToken) shouldReturn future(false)

      Post(s"/sessions/${session.id}").signed.check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

    "return 401 when bearer token is not provided" in new Setup {
      Post(s"/sessions/${session.id}").check {
        status shouldBe StatusCodes.Unauthorized
      }
    }

  }

}
