package orion.service.job

import java.util.UUID

import akka.actor.ActorRef
import akka.testkit.{ TestActorRef, TestProbe }
import cortex.api.job.message.{ CleanUpResources, JobMessage, JobMessageMeta }
import orion.domain.service.job._
import orion.ipc.rabbitmq.MlJobTopology
import orion.testkit.service.ServiceBaseSpec

class JobResourcesCleanerUnitSpec extends ServiceBaseSpec {

  trait Scope extends ServiceScope {
    val jobId: UUID = UUID.randomUUID()
    val jobType = "TRAIN"
    val newWorkerProbe = TestProbe()

    mockRabbitMqClient.subscribeExpects[JobMessage](MlJobTopology.CleanUpResourcesQueue)

    val service = TestActorRef(new JobResourcesCleaner with RabbitMqRpcClientSupportTesting {
      override def newWorker(jobId: UUID): ActorRef = newWorkerProbe.ref
    })
  }

  "When receiving a CleanUpResources msg, the JobResourcesCleaner" should {
    "resend the msg to a new JobResourcesCleaner worker" in new Scope {
      val cleanUpResourcesMsg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CleanUpResources)
      service ! JobMessage(JobMessageMeta(jobId, Some(jobType)), CleanUpResources)

      newWorkerProbe.expectMsg(cleanUpResourcesMsg)
    }
  }
}
