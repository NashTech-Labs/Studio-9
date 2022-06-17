package orion.service.job

import java.util.UUID

import akka.testkit.TestActorRef
import com.rabbitmq.client.impl.AMQImpl
import cortex.api.job.message.{ CleanUpResources, JobMessage, JobMessageMeta }
import mesosphere.marathon.client.model.v2.Result
import orion.ipc.rabbitmq.MlJobTopology.JobMasterInQueueTemplate
import orion.testkit.service.ServiceBaseSpec

import scala.concurrent.Future

class JobResourcesCleanerWorkerUnitSpec extends ServiceBaseSpec {

  trait Scope extends ServiceScope {
    val jobId: UUID = UUID.randomUUID()
    val jobType = "TRAIN"
    val service = TestActorRef(new JobResourcesCleanerWorker with RabbitMqRpcClientSupportTesting with MarathonClientSupportTesting)
  }

  "When receiving a CleanUpResources msg, the JobResourcesCleanerWorker" should {
    "call the RabbitMqClient api for deleting the JobMasterIn queue, call the MarathonClient api for destroying the JobMaster app, and stop itself when done" in new Scope {
      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CleanUpResources)

      // Mock RabbitMqClient call
      mockRabbitMqClient
        .deleteQueueExpects(JobMasterInQueueTemplate.format(jobId))
        .returning(Future.successful(new AMQImpl.Queue.DeleteOk(1)))

      // Mock MarathonClient call
      mockMarathonClient.destroyAppExpects(jobId.toString)
        .returning(Future.successful(Some(new Result())))

      // Send msg
      watch(service)
      service ! cleanUpResourcesMsg
      expectTerminated(service)
    }
    // NOTE: this behaviour might change once we add a proper failure/retry policy.
    // Take a look at: https://sentrana.atlassian.net/browse/COR-184
    "call the RabbitMqClient api for deleting the JobMasterIn queue, do nothing if it fails and continue with the rest of the clean up tasks" in new Scope {
      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CleanUpResources)

      // Mock RabbitMqClient call
      val unexpectedRabbitMqException = new Exception("BOOM!")
      mockRabbitMqClient
        .deleteQueueExpects(JobMasterInQueueTemplate.format(jobId))
        .returning(Future.failed(unexpectedRabbitMqException))

      // Mock MarathonClient call
      mockMarathonClient.destroyAppExpects(jobId.toString)
        .returning(Future.successful(Some(new Result())))

      // Send msg
      watch(service)
      service ! cleanUpResourcesMsg
      expectTerminated(service)
    }
    // NOTE: this behaviour might change once we add a proper failure/retry policy.
    // Take a look at: https://sentrana.atlassian.net/browse/COR-184
    "call the RabbitMqClient api for deleting the JobMasterIn queue, call the MarathonClient api for destroying the JobMaster app, do nothing if it fails and continue with the rest of the clean up tasks" in new Scope {
      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CleanUpResources)

      // Mock RabbitMqClient call
      mockRabbitMqClient
        .deleteQueueExpects(JobMasterInQueueTemplate.format(jobId))
        .returning(Future.successful(new AMQImpl.Queue.DeleteOk(1)))

      // Mock MarathonClient call
      val unexpectedMarathonException = new Exception("BOOM!")
      mockMarathonClient.destroyAppExpects(jobId.toString)
        .returning(Future.failed(unexpectedMarathonException))

      // Send msg
      watch(service)
      service ! cleanUpResourcesMsg
      expectTerminated(service)
    }
  }
}
