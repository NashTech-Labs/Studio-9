package orion.service.job

import java.util.{ Date, UUID }

import akka.testkit.TestActorRef
import com.spingo.op_rabbit.Message
import cortex.api.job.message._
import orion.domain.service.job._
import orion.ipc.rabbitmq.MlJobTopology._
import orion.service.job.JobMessagePublisherWorker.{ MessagePublished, PublishToCleanUpResourcesQueue, PublishToMasterInQueue, PublishToStatusQueue }
import orion.testkit.service.ServiceBaseSpec

import scala.concurrent.duration._
import scala.concurrent.Future

class JobMessagePublisherWorkerUnitSpec extends ServiceBaseSpec {

  import JobSupervisorFixtures._

  trait Scope extends ServiceScope {
    val jobId = UUID.randomUUID()
    val service = TestActorRef(new JobMessagePublisherWorker with RabbitMqRpcClientSupportTesting)
  }

  "When receiving a PublishToMasterInQueue msg, the JobMessagePublisherWorker" should {
    "call the RabbitMqClient api, publish the message and respond with a MessagePublished msg if no errors" in new Scope {
      val submissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))

      // Mock RabbitMq call
      val exchange = GatewayExchange
      val routingKey = JobMasterInRoutingKeyTemplate.format(jobId)
      val queue = JobMasterInQueueTemplate.format(jobId)

      mockRabbitMqClient
        .declareDirectBindingExpects(DataDistributorExchange, routingKey, queue)
        .returning(Future.successful(()))

      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(submissionMsg, exchange, routingKey)
        .returning(Future.successful(Message.Ack(1L)))

      // Send msg
      service ! PublishToMasterInQueue(submissionMsg)

      // Verify response
      expectMsg(MessagePublished(submissionMsg))
    }
    "call the RabbitMqClient api, publish the message and respond with a Failure if there are errors while declaring the queue" in new Scope {
      val submissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))

      // Mock RabbitMq call
      val exchange = GatewayExchange
      val routingKey = JobMasterInRoutingKeyTemplate.format(jobId)
      val queue = JobMasterInQueueTemplate.format(jobId)

      val rabbitMqException = new RuntimeException("BOOM!")
      mockRabbitMqClient
        .declareDirectBindingExpects(DataDistributorExchange, routingKey, queue)
        .returning(Future.failed(rabbitMqException))

      // Send msg
      service ! PublishToMasterInQueue(submissionMsg)

      // Verify response
      expectMsgFailure(rabbitMqException)
    }
    "call the RabbitMqClient api, publish the message and respond with a Failure if there are errors while publishing the msg" in new Scope {
      val submissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))

      // Mock RabbitMq call
      val exchange = GatewayExchange
      val routingKey = JobMasterInRoutingKeyTemplate.format(jobId)
      val queue = JobMasterInQueueTemplate.format(jobId)

      mockRabbitMqClient
        .declareDirectBindingExpects(DataDistributorExchange, routingKey, queue)
        .returning(Future.successful(()))

      val rabbitMqException = new RuntimeException("BOOM!")
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(submissionMsg, exchange, routingKey)
        .returning(Future.failed(rabbitMqException))

      // Send msg
      service ! PublishToMasterInQueue(submissionMsg)

      // Verify response
      expectMsgFailure(rabbitMqException)
    }
  }

  "When receiving a PublishToStatusQueue msg, the JobMessagePublisherWorker" should {
    "call the RabbitMqClient api, publish the message and respond with a MessagePublished msg if no errors" in new Scope {
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))

      // Mock RabbitMq call
      val exchange = GatewayExchange
      val jobStatusRoutingKey = JobStatusRoutingKeyTemplate.format(jobId)
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(heartbeatMsg, exchange, jobStatusRoutingKey)
        .returning(Future.successful(Message.Ack(1L)))

      // Send msg
      service ! PublishToStatusQueue(heartbeatMsg)

      // Verify response
      expectMsg(MessagePublished(heartbeatMsg))
    }
    "call the RabbitMqClient api, publish the message and respond with a Failure if there are errors while publishing the msg" in new Scope {
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))

      // Mock RabbitMq call
      val exchange = GatewayExchange
      val jobStatusRoutingKey = JobStatusRoutingKeyTemplate.format(jobId)
      val rabbitMqException = new RuntimeException("BOOM!")
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(heartbeatMsg, exchange, jobStatusRoutingKey)
        .returning(Future.failed(rabbitMqException))

      // Send msg
      service ! PublishToStatusQueue(heartbeatMsg)

      // Verify response
      expectMsgFailure(rabbitMqException)
    }
  }

  "When receiving a PublishToCleanUpResourcesQueue msg, the JobMessagePublisherWorker" should {
    "call the RabbitMqClient api, publish the message and respond with a MessagePublished msg if no errors" in new Scope {
      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId), CleanUpResources)

      // Mock RabbitMq call
      val exchange = GatewayExchange
      val cleanUpResourcesRoutingKey = CleanUpResourcesRoutingKeyTemplate.format(jobId)
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(cleanUpResourcesMsg, exchange, cleanUpResourcesRoutingKey)
        .returning(Future.successful(Message.Ack(1L)))

      // Send msg
      service ! PublishToCleanUpResourcesQueue(cleanUpResourcesMsg)

      // Verify response
      expectMsg(MessagePublished(cleanUpResourcesMsg))
    }
    "call the RabbitMqClient api, publish the message and respond with a Failure if there are errors while publishing the msg" in new Scope {
      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId), CleanUpResources)

      // Mock RabbitMq call
      val exchange = GatewayExchange
      val cleanUpResourcesRoutingKey = CleanUpResourcesRoutingKeyTemplate.format(jobId)
      val rabbitMqException = new RuntimeException("BOOM!")
      mockRabbitMqClient
        .sendMessageToExchangeWithConfirmationExpects(cleanUpResourcesMsg, exchange, cleanUpResourcesRoutingKey)
        .returning(Future.failed(rabbitMqException))

      // Send msg
      service ! PublishToCleanUpResourcesQueue(cleanUpResourcesMsg)

      // Verify response
      expectMsgFailure(rabbitMqException)
    }
  }
}
