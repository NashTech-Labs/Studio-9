package orion.service.job

import java.util.UUID

import akka.actor.{ ActorRef, Props }
import akka.util.Timeout
import com.rabbitmq.client.AMQP.Queue.DeleteOk
import cortex.api.job.message.{ CleanUpResources, JobMessage, JobMessageMeta }
import mesosphere.marathon.client.model.v2.Result
import orion.common.json4s.OrionJson4sSupport
import orion.common.service.{ MarathonClientSupport, NamedActor, Service }
import orion.common.utils.FutureLift
import orion.ipc.rabbitmq.{ MlJobTopology, RabbitMqRpcClientSupport }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success }

object JobResourcesCleaner extends NamedActor {
  val Name = "job-resources-cleaner"

  def props(): Props = {
    Props(new JobResourcesCleaner())
  }
}

class JobResourcesCleaner extends Service with RabbitMqRpcClientSupport with OrionJson4sSupport {

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  override def preStart(): Unit = {
    subscribe[JobMessage](MlJobTopology.CleanUpResourcesQueue)
  }

  override def receive: Receive = {
    case msg @ JobMessage(JobMessageMeta(jobId, _), CleanUpResources) => newWorker(jobId) ! msg
  }

  def newWorker(jobId: UUID): ActorRef = {
    context.actorOf(JobResourcesCleanerWorker.props(), JobResourcesCleanerWorker.name(jobId))
  }
}

object JobResourcesCleanerWorker {
  def name(jobId: UUID): String = s"job-resources-cleaner-worker-$jobId"

  def props(): Props = {
    Props(new JobResourcesCleanerWorker())
  }
}

class JobResourcesCleanerWorker extends Service with RabbitMqRpcClientSupport with MarathonClientSupport {
  import MlJobTopology._

  implicit val ec: ExecutionContextExecutor = context.dispatcher
  implicit val timeout: Timeout = Timeout(30 seconds)

  override def receive: Receive = {
    case JobMessage(JobMessageMeta(jobId, _), CleanUpResources) => {

      log.info("[ JobId: {} ]: [Clean up resources] - starting resources clean-up process", jobId)

      // NOTE: trigger clean up tasks in parallel
      val deleteQueueFuture = deleteJobMasterInQueue(jobId)
      val destroyAppFuture = destroyJobMasterApp(jobId)

      // Tasks are independent so it doesn't matter if one of them failed so try to complete each task
      FutureLift.lift(Seq(deleteQueueFuture, destroyAppFuture)) onComplete {
        _ => context stop self
      }
    }
  }

  def deleteJobMasterInQueue(jobId: UUID): Future[DeleteOk] = {
    log.info("[ JobId: {} ] - [Clean up resources] - [Delete JobMasterIn queue] - Calling RabbitMq API for deleting queue", jobId)

    deleteQueue(JobMasterInQueueTemplate.format(jobId)) andThen {
      case Success(_) => log.info("[ JobId: {} ] - [Clean up resources] - [Delete JobMasterIn queue] - Queue deletion succeeded", jobId)
      case Failure(e) => log.error("[ JobId: {} ] - [Clean up resources] - [Delete JobMasterIn queue] - Failed to delete queue with error: [{}]", jobId, e)
    }
  }

  def destroyJobMasterApp(jobId: UUID): Future[Option[Result]] = {
    log.info("[ JobId: {} ] - [Clean up resources] - [Destroy JobMaster App] - Calling Marathon API for destroying JobMaster app", jobId)

    destroyApp(jobId.toString) andThen {
      case Success(Some(_)) =>
        log.info("[ JobId: {} ] - [Clean up resources] - [Destroy JobMaster App] - JobMaster app destruction succeeded", jobId)
      case Success(None) =>
        log.warning("[ JobId: {} ] - [Clean up resources] - [Destroy JobMaster App] - Tried to destroy JobMaster app but it does not exist", jobId)
      case Failure(e) =>
        log.error("[ JobId: {} ] - [Clean up resources] - [Destroy JobMaster App] - Failed to destroy JobMaster app with error: [{}]", jobId, e)
    }
  }
}
