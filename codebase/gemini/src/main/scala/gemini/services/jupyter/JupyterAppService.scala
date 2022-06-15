package gemini.services.jupyter

import java.util
import java.util.UUID

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes
import gemini.domain.jupyter.JupyterNodeParams
import gemini.domain.remotestorage.{ S3TemporaryCredentials, TemporaryCredentials }
import gemini.services.jupyter.JupyterAppService.{ AppStatus, ContainerEnvVariables }
import gemini.utils.ThrowableExtensions._
import gemini.utils.TryExtensions._
import mesosphere.marathon.client.model.v2._
import mesosphere.marathon.client.{ Marathon, MarathonException }

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class JupyterAppService(
  val settings: JupyterAppService.Settings,
  val marathon: Marathon
)(implicit val ec: ExecutionContext, log: LoggingAdapter) {

  private val dockerImage = settings.dockerImage
  private val forcePullImage = settings.forcePullImage
  private val defaultNumberOfCPUs = settings.defaultNumberOfCPUs
  private val defaultNumberOfGPUs = settings.defaultNumberOfGPUs
  private val defaultMemory = settings.defaultMemory
  private val heartbeatInterval = settings.heartbeatInterval
  private val projectFilesSyncInterval = settings.projectFilesSyncInterval
  private val geminiHostname = settings.geminiHostname
  private val geminiPort = settings.geminiPort
  private val taskKillGracePeriodSeconds = settings.taskKillGracePeriod.toSeconds
  private val deepcortexUrl = settings.deepcortexUrl
  private val sqlServerUrl = settings.sqlServerUrl
  private val resourceSchedulerUrl = settings.resourceSchedulerUrl

  private type Constraint = java.util.List[String]

  def createApp(
    sessionId: UUID,
    sessionToken: String,
    sessionAccessPath: String,
    tempCredentials: TemporaryCredentials,
    folderPath: String,
    userAuthToken: String,
    geminiAuthToken: String,
    nodeParams: JupyterNodeParams
  ): Future[String] = {

    def buildCredentials: Try[S3TemporaryCredentials] = Try {
      tempCredentials match {
        case creds: S3TemporaryCredentials =>
          creds
        case unknown =>
          throw new RuntimeException(
            s"Unknown type of temp credentials: [ ${unknown.getClass.getCanonicalName} ]"
          )
      }
    }

    def setupContainer(): Try[Container] = Try {
      val dockerInfo = new Docker()
      dockerInfo.setImage(dockerImage)
      dockerInfo.setForcePullImage(forcePullImage)

      val port = new Port()
      port.setHostPort(0)
      port.setContainerPort(8888)

      val container = new Container()
      container.setType("MESOS")
      container.setDocker(dockerInfo)
      container.setPortMappings(List(port).asJava)
      container
    }

    def setupApp(container: Container, s3Credentials: S3TemporaryCredentials): Try[App] = Try {

      val numberOfGPUs = nodeParams.numberOfGpus.getOrElse(defaultNumberOfGPUs)
      val constraints: List[Constraint] =
        if (numberOfGPUs == 0) {
          List(List("cluster", "LIKE", "cpu-jupyter").asJava)
        } else {
          List(List("cluster", "LIKE", "gpu-jupyter").asJava)
        }

      // Haproxy-related part is required to provide external access to Jupyter Lab urls inside the container.
      val labels = Map(
        "HAPROXY_0_VHOST" -> settings.baseJupyterLabDomain,
        "HAPROXY_0_PATH" -> sessionAccessPath,
        "HAPROXY_GROUP" -> "external"
      )

      val network = new Network()
      network.setMode("container/bridge")

      val app = new App()
      app.setId(getAppId(sessionId))
      app.setContainer(container)
      app.setNetworks(List(network).asJava)
      app.setCpus(nodeParams.numberOfCpus.getOrElse[Double](defaultNumberOfCPUs))
      app.setGpus(numberOfGPUs)
      app.setMem(defaultMemory)
      app.setConstraints(constraints.asJava)
      app.setLabels(labels.asJava)
      app.setInstances(1)
      app.setTaskKillGracePeriodSeconds(taskKillGracePeriodSeconds.toInt)

      val envVariables = ContainerEnvVariables(
        sessionId = sessionId,
        sessionToken = sessionToken,
        baseUrl = sessionId.toString,
        awsAccessKey = s3Credentials.accessKey,
        awsSecretKey = s3Credentials.secretKey,
        awsSessionToken = s3Credentials.sessionToken,
        awsRegion = s3Credentials.region,
        bucketName = s3Credentials.bucketName.toString,
        folderName = folderPath,
        heartbeatInterval = heartbeatInterval.toSeconds,
        projectFilesSyncInterval = projectFilesSyncInterval.toSeconds,
        geminiHostname = geminiHostname,
        geminiPort = geminiPort,
        geminiAuthToken = geminiAuthToken,
        baileAuthToken = userAuthToken,
        deepcortexUrl = deepcortexUrl,
        sqlServerUrl = sqlServerUrl,
        resourceSchedulerUrl = resourceSchedulerUrl,
        resourceSchedulerAuthKey = JupyterMesosResourceProvider.combineSessionIdAndToken(sessionId, geminiAuthToken)
      )

      app.setEnv(envVariables.toMarathonContract)
      app
    }

    def createJupyterApp(app: App): Future[App] =
      Future(marathon.createApp(app)) andThen {
        case Success(_) =>
          log.info("[ SessionId: {} ] – App creation succeeded", sessionId)
        case Failure(e) =>
          log.error(
            "[ SessionId: {} ] – Failed to create Jupyter app [{}] with error [{}]",
            sessionId,
            app,
            e.printInfo
          )
      }

    for {
      credentials <- buildCredentials.toFuture
      container <- setupContainer().toFuture
      app <- setupApp(container, credentials).toFuture
      result <- createJupyterApp(app)
    } yield result.getId
  }

  def getAppStatus(appId: String): Future[Option[AppStatus]] =
    getApp(appId).map {
      // Running
      case Some(app) if app.getTasksRunning > 0 => Some(AppStatus.Running)
      // Any other status except from Running: Deploying, Suspended, Waiting or Delayed
      case Some(_) => Some(AppStatus.Unknown)
      case None => None

    }

  def suspendApp(appId: String): Future[Unit] =
    Future(marathon.getApp(appId)).map { response =>
      val app = response.getApp
      app.setInstances(0)
      marathon.updateApp(appId, app, true)
    }

  def destroyApp(appId: String): Future[Unit] =
    Future(marathon.deleteApp(appId))

  private def getAppId(sessionId: UUID) = s"jupyterlab/${sessionId.toString}"

  private def getApp(appId: String): Future[Option[App]] =
    Future(marathon.getApp(appId)).map(response => Some(response.getApp)).recover {
      case e: MarathonException if e.getStatus == StatusCodes.NotFound.intValue => None
    }
}

object JupyterAppService {

  sealed trait AppStatus

  object AppStatus {
    case object Running extends AppStatus
    case object Unknown extends AppStatus
  }

  case class Settings(
    dockerImage: String,
    forcePullImage: Boolean,
    defaultMemory: Double,
    defaultNumberOfCPUs: Double,
    defaultNumberOfGPUs: Double,
    baseJupyterLabDomain: String,
    heartbeatInterval: FiniteDuration,
    projectFilesSyncInterval: FiniteDuration,
    geminiHostname: String,
    geminiPort: Int,
    taskKillGracePeriod: FiniteDuration,
    deepcortexUrl: String,
    sqlServerUrl: String,
    resourceSchedulerUrl: String
  )

  private case class ContainerEnvVariables(
    sessionId: UUID,
    sessionToken: String,
    baseUrl: String,
    awsAccessKey: String,
    awsSecretKey: String,
    awsSessionToken: String,
    awsRegion: String,
    bucketName: String,
    folderName: String,
    heartbeatInterval: Long,
    projectFilesSyncInterval: Long,
    geminiHostname: String,
    geminiPort: Int,
    geminiAuthToken: String,
    baileAuthToken: String,
    deepcortexUrl: String,
    sqlServerUrl: String,
    resourceSchedulerUrl: String,
    resourceSchedulerAuthKey: String
  ) {

    def toMarathonContract: util.Map[String, AnyRef] =
      Map[String, AnyRef](
        "SESSION_ID" -> sessionId.toString,
        "TOKEN" -> sessionToken,
        "BASE_URL" -> baseUrl,
        "AWS_ACCESS_KEY" -> awsAccessKey,
        "AWS_SECRET_KEY" -> awsSecretKey,
        "AWS_SESSION_TOKEN" -> awsSessionToken,
        "AWS_BUCKET" -> bucketName,
        "AWS_REGION" -> awsRegion,
        "FOLDER_NAME" -> folderName,
        "HEARTBEAT_INTERVAL" -> heartbeatInterval.toString,
        "PROJECT_FILES_SYNC_INTERVAL" -> projectFilesSyncInterval.toString,
        "GEMINI_HOSTNAME" -> geminiHostname,
        "GEMINI_PORT" -> geminiPort.toString,
        "GEMINI_AUTH_TOKEN" -> geminiAuthToken,
        "BAILE_AUTH_TOKEN" -> baileAuthToken,
        "DEEPCORTEX_URL" -> deepcortexUrl,
        "SQL_SERVER_URL" -> sqlServerUrl,
        "RESOURCE_SCHEDULER_URL" -> resourceSchedulerUrl,
        "RESOURCE_SCHEDULER_AUTH_KEY" -> resourceSchedulerAuthKey
      ).asJava

  }

}
