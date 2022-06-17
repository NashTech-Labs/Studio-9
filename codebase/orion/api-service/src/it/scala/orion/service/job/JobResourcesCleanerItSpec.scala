package orion.service.job

import java.util.UUID

import akka.actor.{ ActorRef, Props }
import akka.pattern.gracefulStop
import akka.testkit.{ TestActorRef, TestProbe }
import com.rabbitmq.client.AMQP.Queue.DeleteOk
import com.rabbitmq.client.AMQP.Queue.DeleteOk.Builder
import cortex.api.job.message.{ CleanUpResources, JobMessage, JobMessageMeta }
import mesosphere.marathon.client.model.v2.Result
import orion.common.json4s.OrionJson4sSupport
import orion.ipc.rabbitmq.MlJobTopology._
import orion.testkit.service.{ RabbitMqItSupport, ServiceBaseSpec }

import scala.concurrent.Future

class JobResourcesCleanerItSpec extends ServiceBaseSpec with RabbitMqItSupport {

  // Fixtures
  val jobId: UUID = UUID.randomUUID()
  val jobType = "TRAIN"

  trait Scope extends ServiceScope with OrionJson4sSupport {
    val newWorkerProbe = TestProbe()

    val service: ActorRef = system.actorOf(Props(new JobResourcesCleaner {
      override def newWorker(jobId: UUID): ActorRef = newWorkerProbe.ref
    }))
  }

  "When receiving a CleanUpResources msg, the JobResourcesCleaner" should {
    "resend the msg to a new JobResourcesCleaner worker" in new Scope {
      // Send message to queue
      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CleanUpResources)
      sendMessage(cleanUpResourcesMsg, GatewayExchange, CleanUpResourcesRoutingKeyTemplate.format(jobId))

      // Verify msg is forwarded
      newWorkerProbe.expectMsg(cleanUpResourcesMsg)

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
  }
}

class JobResourcesCleanerWorkerItSpec extends ServiceBaseSpec with RabbitMqItSupport {

  // Fixtures
  val jobId: UUID = UUID.randomUUID()
  val jobType = "TRAIN"

  trait Scope extends ServiceScope {
    val service = TestActorRef(new JobResourcesCleanerWorker with MarathonClientSupportTesting with RabbitMqRpcClientSupportTesting)
    val deleteOk: DeleteOk = new Builder().build()
  }

  "When receiving a CleanUpResources msg, the JobResourcesCleanerWorker" should {
    "call the RabbitMqClient api for deleting the JobMasterIn queue, " +
      "call the MarathonClient api for destroying the JobMaster app, and stop itself when done " +
      "if each call is successful" in new Scope {
      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CleanUpResources)

      // Mock RabbitMqClient call
      mockRabbitMqClient.deleteQueueExpects(JobMasterInQueueTemplate.format(jobId))
        .returning(Future.successful(deleteOk))

      // Mock MarathonClient call
      mockMarathonClient.destroyAppExpects(jobId.toString)
        .returning(Future.successful(Some(new Result())))

      // Send msg
      watch(service)
      service ! cleanUpResourcesMsg
      expectTerminated(service)
    }

    "call the RabbitMqClient api for deleting the JobMasterIn queue, " +
      "call the MarathonClient api for destroying the JobMaster app, and stop itself when done " +
      "if the first call isn't successful" in new Scope {
      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CleanUpResources)

      // Mock RabbitMqClient call
      mockRabbitMqClient.deleteQueueExpects(JobMasterInQueueTemplate.format(jobId))
        .returning(Future.failed(new Exception("BOOM!")))

      // Mock MarathonClient call
      mockMarathonClient.destroyAppExpects(jobId.toString)
        .returning(Future.successful(Some(new Result())))

      // Send msg
      watch(service)
      service ! cleanUpResourcesMsg
      expectTerminated(service)
    }
  }

  "call the RabbitMqClient api for deleting the JobMasterIn queue, " +
    "call the MarathonClient api for destroying the JobMaster app, and stop itself when done " +
    "if the second call isn't successful" in new Scope {
    val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CleanUpResources)

    // Mock RabbitMqClient call
    mockRabbitMqClient.deleteQueueExpects(JobMasterInQueueTemplate.format(jobId))
      .returning(Future.successful(deleteOk))

    // Mock MarathonClient call
    mockMarathonClient.destroyAppExpects(jobId.toString)
      .returning(Future.failed(new Exception("BOOM!")))

    // Send msg
    watch(service)
    service ! cleanUpResourcesMsg
    expectTerminated(service)
  }
}
