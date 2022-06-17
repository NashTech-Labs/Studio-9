package orion.common.service

import akka.actor.{ ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import com.typesafe.config.Config
import mesosphere.marathon.client.{ Marathon, MarathonException }
import mesosphere.marathon.client.model.v2.{ App, QueueElement, Result }
import orion.common.service.MarathonClient.AppStatus

import scala.concurrent.{ ExecutionContext, Future }

trait MarathonClient extends Extension {

  import MarathonClient._

  protected[this] val marathon: Marathon
  protected[this] implicit val ec: ExecutionContext

  def createApp(app: App): Future[App] = Future {
    marathon.createApp(app)
  }

  def getApp(appId: String): Future[Option[App]] = {
    Future(marathon.getApp(appId)).map(response => Some(response.getApp))
      .recover {
        case e: MarathonException if e.getStatus == StatusCodes.NotFound.value => None
      }
  }

  // NOTE: Leaving below code as reference of how all different app status could be calculated. Upon discussion with
  // Marathon support team and after running personal tests, it seems that documentation mentioned below
  // is out of date and that the status is not being calculated properly using the described rules.
  /*
  def getAppStatus(appId: String): Future[Option[AppStatus]] = {
    getApp(appId) flatMap {
      // Note: please refer to Marathon UI documentation for explanation of the calculation of the status:
      // https://mesosphere.github.io/marathon/docs/marathon-ui.html#application-status-reference

      // Running
      case Some(app) if app.getTasksRunning > 0                           => Future.successful(Some(AppStatus.Running))

      // Deploying
      case Some(app) if app.getDeployments.size > 0                       => Future.successful(Some(AppStatus.Deploying))

      // Suspended
      case Some(app) if app.getInstances == 0 && app.getTasksRunning == 0 => Future.successful(Some(AppStatus.Suspended))

      case Some(_) => getQueue map {
        // Waiting
        case queue if queue.exists(e => e.getApp.getId == appId && e.getDelay.isOverdue == true) => Some(AppStatus.Waiting)

        // Delayed
        case queue if queue.exists(e => e.getApp.getId == appId && e.getDelay.isOverdue == false) => Some(AppStatus.Delayed)

        // Unknown
        case _ => Some(AppStatus.Unknown)
      }

      case None => Future.successful(None)

    }
  }
  */

  def getAppStatus(appId: String): Future[Option[AppStatus]] = {
    getApp(appId) map {
      // Running
      case Some(app) if app.getTasksRunning > 0 => Some(AppStatus.Running)

      // Any other status except from Running: Deploying, Suspended, Waiting or Delayed
      case Some(_)                              => Some(AppStatus.Unknown)

      case None                                 => None

    }
  }

  def destroyApp(appId: String): Future[Option[Result]] = {
    Future(marathon.deleteApp(appId)).map(response => Some(response))
      .recover {
        case e: MarathonException if e.getStatus == StatusCodes.NotFound.value => None
      }
  }

  def getQueue(): Future[Seq[QueueElement]] = {
    import collection.JavaConverters._
    Future(marathon.getQueue).map(response => response.getQueue.asScala.toSeq)
  }

}

object MarathonClient extends ExtensionId[MarathonClient] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): MarathonClient = new MarathonClient {
    override protected val marathon: Marathon = {
      val settings = MarathonClientSettings(system)
      mesosphere.marathon.client.MarathonClient.getInstance(settings.endpoint)
    }

    // NOTE: using default dispatcher for now, if running out of threads use a dedicated ExecutionContext
    override protected implicit val ec: ExecutionContext = system.dispatcher
  }

  override def lookup(): ExtensionId[_ <: Extension] = MarathonClient

  // Marathon Objects
  sealed trait StatusCode {
    val value: Int
  }

  object StatusCodes {
    case object NotFound extends StatusCode {
      override val value: Int = 404
    }
  }

  sealed trait AppStatus
  object AppStatus {
    case object Running extends AppStatus
    case object Deploying extends AppStatus
    case object Suspended extends AppStatus
    case object Delayed extends AppStatus
    case object Waiting extends AppStatus
    case object Unknown extends AppStatus
  }
}

// Actor support
trait MarathonClientSupport { self: Service =>

  val marathonClient = MarathonClient(context.system)

  def createApp(app: App): Future[App] = marathonClient.createApp(app)

  def getApp(appId: String): Future[Option[App]] = marathonClient.getApp(appId)

  def getAppStatus(appId: String): Future[Option[AppStatus]] = marathonClient.getAppStatus(appId)

  def destroyApp(appId: String): Future[Option[Result]] = marathonClient.destroyApp(appId)

  def getQueue(): Future[Seq[QueueElement]] = marathonClient.getQueue()

}

// Settings
class MarathonClientSettings(config: Config) extends Extension {
  private val marathonConfig = config.getConfig("marathon-client")
  val endpoint = marathonConfig.getString("marathon-endpoint")
}

object MarathonClientSettings extends ExtensionId[MarathonClientSettings] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): MarathonClientSettings = new MarathonClientSettings(system.settings.config)

  override def lookup(): ExtensionId[_ <: Extension] = MarathonClientSettings
}
