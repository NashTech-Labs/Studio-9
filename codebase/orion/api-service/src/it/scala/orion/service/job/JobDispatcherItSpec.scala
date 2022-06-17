package orion.service.job

import java.util.Date

import akka.actor.Props
import akka.pattern.gracefulStop
import akka.testkit.TestProbe
import cortex.api.job.message._
import orion.common.json4s.OrionJson4sSupport
import orion.ipc.rabbitmq.MlJobTopology._
import orion.testkit.service.{ RabbitMqItSupport, ServiceBaseSpec }

import scala.concurrent.duration._

class JobDispatcherItSpec extends ServiceBaseSpec with RabbitMqItSupport {

  // Fixtures
  val jobId = mockRandomUUID
  val jobType = "TRAIN"
  val inputPath = "some/input/path"
  val created = new Date().withoutMillis()
  val currentProgress = 0.1D
  val estimatedTimeRemaining = 2 hours

  trait Scope extends ServiceScope with OrionJson4sSupport {
    val jobSupervisorShardRegionProbe = TestProbe()

    val service = system.actorOf(Props(new JobDispatcher(jobSupervisorShardRegionProbe.ref)))
  }

  "When submitting a Message to the NewJob queue, the service" should {
    "read the message and forward it to the JobSupervisorRegion" in new Scope {
      // Send message to queue
      val msg = JobMessage(JobMessageMeta(jobId, Some(jobType)), SubmitJob(inputPath))
      sendMessage(msg, GatewayExchange, NewJobRoutingKeyTemplate.format(jobId))

      // Verify msg is forwarded
      jobSupervisorShardRegionProbe.expectMsg(msg)

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
  }

  "When submitting a Message to the JobMasterOut queue , the service" should {
    "read the message and forward it to the JobSupervisorRegion" in new Scope {
      // Send message to queue
      val msg = JobMessage(JobMessageMeta(jobId, Some(jobType)), Heartbeat(created, currentProgress, Some(estimatedTimeRemaining)))

      sendMessage(msg, GatewayExchange, JobMasterOutRoutingKeyTemplate.format(jobId))

      // Verify msg is forwarded
      jobSupervisorShardRegionProbe.expectMsg(msg)

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
  }

  "When submitting a Message to the CancelJob queue , the service" should {
    "read the message and forward it to the JobSupervisorRegion" in new Scope {
      // Send message to queue
      val msg = JobMessage(JobMessageMeta(jobId, Some(jobType)), CancelJob)
      sendMessage(msg, GatewayExchange, CancelJobRoutingKeyTemplate.format(jobId))

      // Verify msg is forwarded
      jobSupervisorShardRegionProbe.expectMsg(msg)

      // Stop actor when done
      gracefulStop(service, timeout.duration).futureValue shouldBe true
    }
  }

}
