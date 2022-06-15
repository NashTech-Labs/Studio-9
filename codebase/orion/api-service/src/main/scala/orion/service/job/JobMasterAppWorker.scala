package orion.service.job

import java.util.UUID

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props }
import akka.pattern.pipe
import com.typesafe.config.Config
import mesosphere.marathon.client.model.v2.{ App, Container, Docker, Parameter }
import orion.common.service.{ MarathonClient, MarathonClientSupport, NamedActor, Service }
import orion.common.utils.TryExtensions._

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

class JobMasterSettings(config: Config) extends Extension {
  private val jobMasterConfig = config.getConfig("job-master")
  val dockerImage = jobMasterConfig.getString("docker-image")
  val forceDockerPull = jobMasterConfig.getBoolean("force-docker-pull")
  val appEnvironmentVariables = getAppEnvironmentVariables()
  val mesosMasterAddress = jobMasterConfig.getString("mesos-master-address")
  val cpus = jobMasterConfig.getDouble("cpus")
  val memory = jobMasterConfig.getDouble("memory")
  val jvmHeapSizeFactor = jobMasterConfig.getInt("jvm-heapsize-factor")
  val instances = jobMasterConfig.getInt("instances")

  private def getAppEnvironmentVariables(): Map[String, String] = {
    jobMasterConfig.getConfigList("app-environment-variables").filterNot(_.getString("value").isEmpty()).map { c =>
      (c.getString("name"), c.getString("value"))
    }.toMap
  }
}

object JobMasterSettings extends ExtensionId[JobMasterSettings] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): JobMasterSettings = new JobMasterSettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = JobMasterSettings

}

object JobMasterAppWorker extends NamedActor {
  val Name = "job-master-app-worker"

  def props(): Props = {
    Props(new JobMasterAppWorker())
  }

  case class CreateJobMasterApp(jobId: UUID)
  case class JobMasterAppCreated(app: App)

  case class GetJobMasterAppStatus(jobId: UUID)
  case class GetJobMasterAppStatusResult(appStatus: Option[MarathonClient.AppStatus])
}

class JobMasterAppWorker extends Service with MarathonClientSupport {
  import JobMasterAppWorker._

  implicit val ec = context.dispatcher

  val settings = JobMasterSettings(context.system)

  // TODO: add proper failure/retry policy. Take a look at:
  // https://sentrana.atlassian.net/browse/COR-184
  def receive: Receive = {
    case CreateJobMasterApp(jobId)    => createJobMasterApp(jobId) map JobMasterAppCreated pipeTo sender

    case GetJobMasterAppStatus(jobId) => getJobMasterAppStatus(jobId) map GetJobMasterAppStatusResult pipeTo sender
  }

  def createJobMasterApp(jobId: UUID): Future[App] = {
    val dockerImage = settings.dockerImage
    val forceDockerPull = settings.forceDockerPull
    val appEnvironmentVariables = settings.appEnvironmentVariables
    val mesosMasterAddress = settings.mesosMasterAddress
    val cpus = settings.cpus
    val memory = settings.memory
    val instances = settings.instances
    val jvmHeapSizeFactor = settings.jvmHeapSizeFactor
    val constraints = List(
      List("cluster", "UNLIKE", "gpu").asJava,
      List("cluster", "UNLIKE", "gpu-jupyter").asJava,
      List("cluster", "UNLIKE", "cpu-jupyter").asJava
    ).asJava
    require(jvmHeapSizeFactor > 0 && jvmHeapSizeFactor <= 100)

    def setupContainer(dockerImage: String, forceDockerPull: Boolean): Try[Container] = Try {
      val container = new Container()
      container.setType("DOCKER")

      val dockerInfo = new Docker()
      dockerInfo.setImage(dockerImage)
      dockerInfo.setNetwork("HOST")
      dockerInfo.setForcePullImage(forceDockerPull)

      val javaOpts = {
        val xmx = ((jvmHeapSizeFactor * memory) / 100).toInt
        val xms = if ((xmx / 2) > 0) xmx / 2 else xmx
        log.info(s"[ JobId: {} ] allocating jvm max heap-size - $xmx, initial heap-size - $xms", jobId)
        new Parameter("env", s"JAVA_OPTS=-Xms${xms}m -Xmx${xmx}m")
      }

      val params = Seq(
        new Parameter("rm", "true"),
        new Parameter("net", "host"),
        javaOpts
      )
      dockerInfo.setParameters(params)
      container.setDocker(dockerInfo)

      container
    }

    def setupApp(container: Container): Try[App] = Try {
      val app = new App()
      app.setId(jobId.toString)
      app.setContainer(container)
      app.setCpus(cpus)
      app.setMem(memory)
      app.setInstances(instances)
      app.setConstraints(constraints)
      app.setArgs(Seq("service", "--job-id", jobId.toString, "--mesos-master", mesosMasterAddress))
      app.setEnv(appEnvironmentVariables)
      app.setPorts(Seq(new Integer(0))) // NOTE: Setting random port for task notifier, it can be access via $PORT0
      app

    }

    def createJobMasterApp(app: App): Future[App] = {
      createApp(app) andThen {
        case Success(_) => log.info("[ JobId: {} ] - [Create Job Master App] - JobMaster app creation succeeded", jobId)
        case Failure(e) => log.error("[ JobId: {} ] - [Create Job Master App] - Failed to create JobMaster app with error [{}]", jobId, app, e)
      }
    }

    log.info("[ JobId: {} ] - [Create Job Master App] - Creating JobMaster app", jobId)

    val result: Future[App] =
      for {
        container <- setupContainer(dockerImage, forceDockerPull).toFuture
        app <- setupApp(container).toFuture
        result <- createJobMasterApp(app)
      } yield result

    result
  }

  def getJobMasterAppStatus(jobId: UUID): Future[Option[MarathonClient.AppStatus]] = {
    log.info("[ JobId: {} ] - [Get Job Master App Status] - Retrieving JobMaster app status")
    val result = getAppStatus(jobId.toString)
    result andThen {
      case Success(_) => log.info("[ JobId: {} ] - [Get Job Master App Status] - JobMaster app status retrieval succeeded", jobId)
      case Failure(e) => log.error("[ JobId: {} ] - [Get Job Master App Status] - Failed to retrieve JobMaster app status with error [{}]", jobId, e)
    }
  }

}
