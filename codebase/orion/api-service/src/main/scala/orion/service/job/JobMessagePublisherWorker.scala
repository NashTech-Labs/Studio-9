package orion.service.job

import akka.actor.Props
import akka.pattern.pipe
import akka.util.Timeout
import com.spingo.op_rabbit.Message.Ack
import cortex.api.job.message.JobMessage
import orion.common.json4s.OrionJson4sSupport
import orion.common.service.{ NamedActor, Service }
import orion.ipc.rabbitmq.MlJobTopology._
import orion.ipc.rabbitmq.{ MlJobTopology, RabbitMqRpcClientSupport }

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{ Failure, Success }

object JobMessagePublisherWorker extends NamedActor {

  val Name = "job-message-publisher-worker"

  def props(): Props = {
    Props(new JobMessagePublisherWorker)
  }

  case class PublishToMasterInQueue(jobMessage: JobMessage)
  case class PublishToStatusQueue(jobMessage: JobMessage)
  case class PublishToCleanUpResourcesQueue(jobMessage: JobMessage)

  case class MessagePublished(jobMessage: JobMessage)
}

class JobMessagePublisherWorker extends Service with OrionJson4sSupport with RabbitMqRpcClientSupport {
  import JobMessagePublisherWorker._

  implicit val ec = context.dispatcher
  implicit val timeout = Timeout(30 seconds)

  // TODO: add proper failure/retry policy. Take a look at:
  // https://sentrana.atlassian.net/browse/COR-184
  def receive: Receive = {
    case PublishToMasterInQueue(jobMessage: JobMessage) =>
      publishToMasterInQueue(jobMessage) map (_ => MessagePublished(jobMessage)) pipeTo sender

    case PublishToStatusQueue(jobMessage: JobMessage) =>
      publishToStatusQueue(jobMessage) map (_ => MessagePublished(jobMessage)) pipeTo sender

    case PublishToCleanUpResourcesQueue(jobMessage: JobMessage) =>
      publishToCleanUpResourcesQueue(jobMessage) map (_ => MessagePublished(jobMessage)) pipeTo sender
  }

  def publishToMasterInQueue(jobMessage: JobMessage): Future[Ack] = {
    val jobId = jobMessage.meta.jobId
    val exchange = MlJobTopology.GatewayExchange
    val routingKey = MlJobTopology.JobMasterInRoutingKeyTemplate.format(jobId)
    val queue = MlJobTopology.JobMasterInQueueTemplate.format(jobId)

    log.info("[ JobId: {} ] - [Publish To MasterIn queue] - Publishing msg to RabbitMq: [{}]", jobId, jobMessage)

    val result: Future[Ack] =
      for {
        _ <- declareDirectBinding(MlJobTopology.DataDistributorExchange, routingKey, queue)
        result <- sendMessageToExchangeWithConfirmation(jobMessage, exchange, routingKey)
      } yield result

    result onComplete {
      case Success(_) => log.info("[ JobId: {} ] - [Publish To JobMasterIn queue] - Msg publishing succeeded: [{}]", jobId, jobMessage)
      case Failure(e) => log.error("[ JobId: {} ] - [Publish To JobMasterIn queue] - Failed to publish msg [{}] with error: [{}]", jobId, jobMessage, e)
    }

    result
  }

  def publishToStatusQueue(jobMessage: JobMessage): Future[Ack] = {
    val jobId = jobMessage.meta.jobId
    val exchange = GatewayExchange
    val routingKey = JobStatusRoutingKeyTemplate.format(jobId)

    log.info("[ JobId: {} ] - [Publish To Status queue] - Publishing msg to RabbitMq: [{}]", jobId, jobMessage)

    sendMessageToExchangeWithConfirmation(jobMessage, exchange, routingKey) andThen {
      case Success(_) => log.info("[ JobId: {} ] - [Publish To Status queue] - Msg publishing succeeded: [{}]", jobId, jobMessage)
      case Failure(e) => log.error("[ JobId: {} ] - [Publish To Status queue] - Failed to publish msg [{}] with error: [{}]", jobId, jobMessage, e)
    }
  }

  def publishToCleanUpResourcesQueue(jobMessage: JobMessage): Future[Ack] = {
    val jobId = jobMessage.meta.jobId
    val exchange = GatewayExchange
    val routingKey = CleanUpResourcesRoutingKeyTemplate.format(jobId)

    log.info("[ JobId: {} ] - [Publish To CleanUp queue] - Publishing msg to RabbitMq: [{}]", jobId, jobMessage)

    sendMessageToExchangeWithConfirmation(jobMessage, exchange, routingKey) andThen {
      case Success(_) => log.info("[ JobId: {} ] - [Publish To CleanUp queue] - Msg publishing succeeded: [{}]", jobId, jobMessage)
      case Failure(e) => log.error("[ JobId: {} ] - [Publish To CleanUp queue] - Failed to publish msg [{}] with error: [{}]", jobId, jobMessage, e)
    }
  }
}
