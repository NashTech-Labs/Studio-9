package orion.service.job

import java.util.{Date, UUID}

import com.spingo.op_rabbit.Message
import cortex.api.job.message._
import orion.common.json4s.OrionJson4sSupport
import orion.domain.service.job._
import orion.ipc.rabbitmq.MlJobTopology._
import orion.service.job.JobMessagePublisherWorker.{MessagePublished, PublishToCleanUpResourcesQueue, PublishToMasterInQueue, PublishToStatusQueue}
import orion.testkit.service.{ RabbitMqItSupport, ServiceBaseSpec }

import scala.concurrent.duration._

class JobMessagePublisherWorkerItSpec extends ServiceBaseSpec with RabbitMqItSupport {

  // Fixtures
  val jobId = UUID.randomUUID()
  val jobType = "TRAIN"
  val inputPath = "some/input/path"
  val outputPath = "some/output/path"
  val completedAt = new Date().withoutMillis()
  val created = new Date().withoutMillis()
  val currentProgress = 0.1D
  val estimatedTimeRemaining = 2 hours

  trait Scope extends ServiceScope with OrionJson4sSupport {
    val service = system.actorOf(JobMessagePublisherWorker.props())
  }

  "When receiving a PublishToMasterInQueue msg, the JobMessagePublisherWorker" should {
    "call the RabbitMqClient api, publish the message and respond with a MessagePublished msg if no errors" in new Scope {
      // Send msg
      val submissionMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      service ! PublishToMasterInQueue(submissionMsg)

      // Verify service response
      expectMsgPF() {
        case MessagePublished(_) => succeed
      }

      // Verify msg has been published to RabbitMq
      val expectedQueue = JobMasterInQueueTemplate.format(jobId)
      val expectedRoutingKey = JobMasterInRoutingKeyTemplate.format(jobId)
      getMessage[JobMessage](expectedQueue).futureValue shouldBe submissionMsg
    }
  }

  "When receiving a PublishToStatusQueue msg, the JobMessagePublisherWorker" should {
    "call the RabbitMqClient api, publish the message and respond with a MessagePublished msg if no errors" in new Scope {
      // Send msg
      val heartbeatMsg = JobMessage(JobMessageMeta(jobId), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))
      service ! PublishToStatusQueue(heartbeatMsg)

      // Verify response
      expectMsgPF() {
        case MessagePublished(_) => succeed
      }

      // Verify msg has been published to RabbitMq
      val expectedQueue = JobStatusQueue
      val expectedRoutingKey = JobStatusRoutingKeyTemplate.format(jobId)
      getMessage[JobMessage](JobStatusQueue).futureValue shouldBe heartbeatMsg
    }
  }

  "When receiving a PublishToCleanUpResourcesQueue msg, the JobMessagePublisherWorker" should {
    "call the RabbitMqClient api, publish the message and respond with a MessagePublished msg if no errors" in new Scope {
      // Send msg
      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId), CleanUpResources)
      service ! PublishToCleanUpResourcesQueue(cleanUpResourcesMsg)

      // Verify response
      expectMsgPF() {
        case MessagePublished(_) => succeed
      }

      // Verify msg has been published to RabbitMq
      val expectedQueue = JobStatusQueue
      val expectedRoutingKey = CleanUpResourcesRoutingKeyTemplate.format(jobId)
      getMessage[JobMessage](CleanUpResourcesQueue).futureValue shouldBe cleanUpResourcesMsg
    }
  }
}
