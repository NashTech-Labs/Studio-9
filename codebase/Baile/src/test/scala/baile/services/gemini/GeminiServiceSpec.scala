package baile.services.gemini

import java.time.Instant
import akka.actor.ExtendedActorSystem
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes }
import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.services.http.exceptions.UnexpectedResponseException
import cortex.api.gemini.{ JupyterNodeParamsRequest, JupyterSessionRequest, JupyterSessionResponse, SessionStatus }

class GeminiServiceSpec extends ExtendedBaseSpec {

  trait Setup {

    val extendedActorSystem: ExtendedActorSystem = mock[ExtendedActorSystem]
    val http: HttpExt = mock[HttpExt]
    val sessionId: String = randomString()
    val sessionToken: String = randomString()
    val sessionUrl: String = randomString()

    val geminiService = new GeminiService(conf, http)

    val jupyterSessionRequest = JupyterSessionRequest(
      userAccessToken = randomString(),
      awsRegion = "us-east-1",
      awsAccessKey = randomString(),
      awsSecretKey = randomString(),
      awsSessionToken = randomString(),
      bucketName = randomString(),
      projectPath = randomPath(),
      nodeParams = JupyterNodeParamsRequest(None, None)
    )

    val jupyterSessionResponse = JupyterSessionResponse(
      id = sessionId,
      token = sessionToken,
      url = sessionUrl,
      status = SessionStatus.Running,
      startedAt = Instant.now()
    )

    http.system shouldReturn extendedActorSystem
    extendedActorSystem.log shouldReturn logger
    http.defaultClientHttpsContext shouldReturn null // scalastyle:off null
  }


  "GeminiService#createSession" should {

    "create a new session" in new Setup {
      http.singleRequest(*, *, *, *) shouldReturn future(HttpResponse(
        status = StatusCodes.Created, entity = httpEntity(jupyterSessionResponse)
      ))

      whenReady(
        geminiService.createSession(jupyterSessionRequest)
      )(_ shouldBe jupyterSessionResponse)
    }

    "return unexpected exception if the response status code is not 201" in new Setup {
      http.singleRequest(*, *, *, *) shouldReturn future(HttpResponse(
        status = StatusCodes.OK, entity = httpEntity(jupyterSessionResponse)
      ))

      whenReady(
        geminiService.createSession(jupyterSessionRequest).failed
      )(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "GeminiService#getSessionStatus" should {

    "get session status" in new Setup {
      http.singleRequest(*, *, *, *) shouldReturn future(HttpResponse(
        status = StatusCodes.OK, entity = httpEntity(jupyterSessionResponse)
      ))

      whenReady(
        geminiService.getSession(sessionId)
      )(_ shouldBe jupyterSessionResponse)
    }

    "return unexpected exception if the response status code is not 200" in new Setup {
      http.singleRequest(*, *, *, *) shouldReturn future(HttpResponse(
        status = StatusCodes.BadRequest, entity = httpEntity(jupyterSessionResponse)
      ))

      whenReady(
        geminiService.getSession(sessionId).failed
      )(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "GeminiService#cancelSession" should {

    "cancel a session" in new Setup {
      http.singleRequest(*, *, *, *) shouldReturn future(HttpResponse(status = StatusCodes.OK))

      geminiService.cancelSession(sessionId).futureValue
    }

    "return unexpected exception if the response status code is not 200" in new Setup {
      http.singleRequest(*, *, *, *) shouldReturn future(HttpResponse(status = StatusCodes.BadRequest))

      whenReady(
        geminiService.cancelSession(sessionId).failed
      )(_ shouldBe an[UnexpectedResponseException])
    }

  }

}
