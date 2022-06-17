package gemini.services.jupyter

import java.util.UUID

import gemini.BaseSpec
import gemini.services.jupyter.JupyterMesosResourceProvider._
import gemini.RandomGenerators._

import scala.concurrent.duration._

class JupyterMesosResourceProviderSpec extends BaseSpec {

  trait Setup {
    val jupyterSessionService = mock[JupyterSessionService]
    val jupyterMesosResourceProvider = new JupyterMesosResourceProvider(
      cpusPerSlave = randomInt(1),
      memoryPerSlave = randomInt(2),
      maxMachines = randomInt(3),
      maxCpus = randomInt(4),
      maxGpus = randomInt(1),
      mesosFrameworksMonitor = testActor,
      actorAskTimeout = 2.seconds,
      jupyterSessionService = jupyterSessionService
    )
  }

  "JupyterMesosResourceProvider" should {
    "transform session id and session token properly" in {
      val expectedSessionId = UUID.randomUUID()
      val expectedSessionToken = UUID.randomUUID().toString

      val (sessionId, sessionToken) = splitSessionIdAndToken(
        combineSessionIdAndToken(expectedSessionId, expectedSessionToken)
      )

      sessionId shouldBe expectedSessionId
      sessionToken shouldBe expectedSessionToken
    }
  }

  "JupyterMesosResourceProvider#authorize" should {
    "utilize JupyterSessionService for authentication" in new Setup {
      val sessionId = UUID.randomUUID()
      val sessionToken = UUID.randomUUID().toString

      jupyterSessionService.authenticate(sessionId, sessionToken) shouldReturn future(true)

      whenReady(jupyterMesosResourceProvider.authorize(sessionId + "_" + sessionToken)) {
        _ shouldBe Right(())
      }
    }

    "wrap JupyterSessionService error" in new Setup {
      jupyterSessionService.authenticate(*, *) shouldReturn future(false)

      whenReady(jupyterMesosResourceProvider.authorize(UUID.randomUUID() + "_" + randomString())) {
        _ shouldBe Left(JupyterMesosResourceProvider.JupyterAuthorizationError)
      }
    }
  }

}
