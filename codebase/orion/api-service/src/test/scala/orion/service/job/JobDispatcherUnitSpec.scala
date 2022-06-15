package orion.service.job

import java.util.UUID

import akka.testkit.{ TestActorRef, TestProbe }
import cortex.api.job.message.{ JobMessage, JobMessageMeta, SubmitJob }
import orion.domain.service.job._
import orion.ipc.rabbitmq.MlJobTopology
import orion.testkit.service.ServiceBaseSpec

class JobDispatcherUnitSpec extends ServiceBaseSpec {

  trait Scope extends ServiceScope {
    val jobSupervisorShardRegionProbe = TestProbe()

    mockRabbitMqClient.subscribeExpects[JobMessage](MlJobTopology.NewJobQueue)
    mockRabbitMqClient.subscribeExpects[JobMessage](MlJobTopology.JobMasterOutQueue)
    mockRabbitMqClient.subscribeExpects[JobMessage](MlJobTopology.CancelJobQueue)

    val service = TestActorRef(new JobDispatcher(jobSupervisorShardRegionProbe.ref) with RabbitMqRpcClientSupportTesting)
  }

  "When receiving a message, the JobDispatcher" should {
    "forward the message to the JobSupervisorRegion if it's a JobMessage" in new Scope {
      val jobMessage = JobMessage(JobMessageMeta(UUID.randomUUID(), Some("TRAIN")), SubmitJob("some/input/path"))
      service ! jobMessage
      jobSupervisorShardRegionProbe.expectMsg(jobMessage)
    }
    "do not forward the message if it's not a JobMessage" in new Scope {
      val nonJobMessage = "non-job-message"
      service ! nonJobMessage
      jobSupervisorShardRegionProbe.expectNoMessage()
    }
  }

}
