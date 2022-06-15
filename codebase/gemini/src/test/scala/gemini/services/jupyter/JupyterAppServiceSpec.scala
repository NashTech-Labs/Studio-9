package gemini.services.jupyter

import java.util.UUID

import gemini.BaseSpec
import gemini.RandomGenerators._
import gemini.domain.jupyter.JupyterNodeParams
import gemini.domain.remotestorage.{ S3TemporaryCredentials, TemporaryCredentials }
import gemini.services.jupyter.JupyterAppService.AppStatus.{ Running, Unknown }
import mesosphere.marathon.client.model.v2.{ GetAppResponse, Result, VersionedApp }
import mesosphere.marathon.client.{ Marathon, MarathonException }

import scala.concurrent.duration._

class JupyterAppServiceSpec extends BaseSpec {

  trait Setup {

    val settings = JupyterAppService.Settings(
      dockerImage = "jupyter-lab",
      forcePullImage = randomBoolean(),
      defaultMemory = 1024,
      defaultNumberOfCPUs = randomInt(1, 4),
      defaultNumberOfGPUs = randomInt(0, 2),
      baseJupyterLabDomain = "jupyter-lab.dc",
      heartbeatInterval = 10.seconds,
      projectFilesSyncInterval = 1.minute,
      geminiHostname = "gemini.ai",
      geminiPort = 9000,
      taskKillGracePeriod = 3.seconds,
      deepcortexUrl = "http://baile.ai/v2.0",
      sqlServerUrl = "http://sql.server.ai/v1.0",
      resourceSchedulerUrl = "http://gemini.ai/v1.0",
    )

    val geminiAuthToken: String = randomString()

    val marathon: Marathon = mock[Marathon]

    val service = new JupyterAppService(settings, marathon)

    val appId: String = UUID.randomUUID().toString
  }

  "JupyterAppService#createApp" should {

    val sessionId = UUID.randomUUID()
    val sessionToken = randomString()
    val sessionAccessPath = "/42/"
    val creds = S3TemporaryCredentials("us-east-1", "bucket", randomString(), randomString(), randomString())
    val folderPath = "folder/path"
    val userAuthToken = randomString()
    val jupyterNodeParams = JupyterNodeParams(
      numberOfCpus = randomOf(None, Some(randomInt(1, 4))),
      numberOfGpus = randomOf(None, Some(randomInt(1, 4)))
    )

    "create new app" in new Setup {
      val app = new VersionedApp()
      app.setId(appId)

      marathon.createApp(*) shouldReturn app

      whenReady(
        service.createApp(
          sessionId = sessionId,
          sessionToken = sessionToken,
          tempCredentials = creds,
          folderPath = folderPath,
          userAuthToken = userAuthToken,
          nodeParams = jupyterNodeParams,
          sessionAccessPath = sessionAccessPath,
          geminiAuthToken = geminiAuthToken
        )
      )(_ shouldBe appId)
    }

    "throw error when unknown type of credentials were provided" in new Setup {
      whenReady(
        service.createApp(
          sessionId = sessionId,
          sessionToken = sessionToken,
          tempCredentials = new TemporaryCredentials {},
          folderPath = folderPath,
          userAuthToken = userAuthToken,
          nodeParams = jupyterNodeParams,
          sessionAccessPath = sessionAccessPath,
          geminiAuthToken = geminiAuthToken
        ).failed
      )(_ should not be a[NullPointerException])
    }

  }

  "JupyterAppService#getAppStatus" should {

    "return running status when there are running tasks in app" in new Setup {
      val app = new VersionedApp
      app.setTasksRunning(1)
      val response = new GetAppResponse
      response.setApp(app)
      marathon.getApp(appId) shouldReturn response

      whenReady(service.getAppStatus(appId))(_ shouldBe Some(Running))
    }

    "return unknown status when there are no running tasks in app" in new Setup {
      val app = new VersionedApp
      app.setTasksRunning(0)
      val response = new GetAppResponse
      response.setApp(app)
      marathon.getApp(appId) shouldReturn response

      whenReady(service.getAppStatus(appId))(_ shouldBe Some(Unknown))
    }

    "return nothing when app is not found" in new Setup {
      marathon.getApp(appId) shouldThrow new MarathonException(404, "No app with this id")

      whenReady(service.getAppStatus(appId))(_ shouldBe None)
    }

  }

  "JupyterAppService#destroyApp" should {
    "delete app" in new Setup {
      marathon.deleteApp(appId) shouldReturn new Result

      service.destroyApp(appId).futureValue
    }

    "return failed Future if marathon threw an exception" in new Setup {
      marathon.deleteApp(appId) shouldThrow new MarathonException(500, "Any error")

      whenReady(service.destroyApp(appId).failed)(_ shouldBe a[MarathonException])
    }
  }

  "JupyterAppService#suspendApp" should {
    "suspend app" in new Setup {
      private val app = mock[VersionedApp]
      private val response = new GetAppResponse
      response.setApp(app)
      marathon.getApp(appId) shouldReturn response
      marathon.updateApp(appId, app, true) shouldReturn new Result

      whenReady(service.suspendApp(appId)) { _ =>
        app.setInstances(0) wasCalled once
      }
    }

    "return failed Future if marathon threw an exception during getting an app" in new Setup {
      val response = new GetAppResponse
      response.setApp(new VersionedApp)
      marathon.getApp(appId) shouldThrow new MarathonException(500, "Any error")

      whenReady(service.suspendApp(appId).failed)(_ shouldBe a[MarathonException])
    }

    "return failed Future if marathon threw an exception during updating an app" in new Setup {
      val response = new GetAppResponse
      response.setApp(new VersionedApp)
      marathon.getApp(appId) shouldReturn response
      marathon.updateApp(appId, *, *) shouldThrow new MarathonException(500, "Any error")

      whenReady(service.suspendApp(appId).failed)(_ shouldBe a[MarathonException])
    }
  }

}
