package orion.service.job

import java.util.{ Date, UUID }

import akka.actor.ActorSystem
import akka.serialization.{ SerializationExtension, SerializerWithStringManifest }
import cortex.api.job.message._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.concurrent.duration._

class JobMessageSerializationUnitSpec extends FlatSpec with TableDrivenPropertyChecks {
  private val system = ActorSystem("job-message-serialization-spec")
  private val serialization = SerializationExtension(system)

  val jobMessages = Table(
    "job-message",
    JobMessage(JobMessageMeta(UUID.randomUUID())),
    JobMessage(JobMessageMeta(UUID.randomUUID(), Some("ds"))),
    JobMessage(JobMessageMeta(UUID.randomUUID()), CancelJob),
    JobMessage(JobMessageMeta(UUID.randomUUID()), GetJobStatus),
    JobMessage(JobMessageMeta(UUID.randomUUID()), CleanUpResources),
    JobMessage(JobMessageMeta(UUID.randomUUID()), JobMasterAppReadyForTermination),
    JobMessage(JobMessageMeta(UUID.randomUUID()), Heartbeat(new Date(), 0.4, Some(5 seconds))),
    JobMessage(JobMessageMeta(UUID.randomUUID()), SubmitJob("some/path")),
    JobMessage(JobMessageMeta(UUID.randomUUID()), JobStarted(new Date())),
    JobMessage(JobMessageMeta(UUID.randomUUID()), JobResultSuccess(
      new Date(),
      Seq(TaskTimeInfo("task1", TimeInfo(new Date, Some(new Date), Some(new Date)))),
      tasksQueuedTime = 2 minutes,
      "some/path"
    )),
    JobMessage(JobMessageMeta(UUID.randomUUID()), JobResultFailure(new Date(), "some/path", "error"))
  )

  "JobMessage" should "be serializer properly" in {
    forAll(jobMessages) { expectedJobMessage =>
      val serializer = serialization.findSerializerFor(expectedJobMessage).asInstanceOf[SerializerWithStringManifest]

      val bytes = serializer.toBinary(expectedJobMessage)
      val emptyJobMessage = serializer.fromBinary(bytes, "cortex.api.job.message.JobMessage")

      emptyJobMessage shouldBe expectedJobMessage
    }
  }
}
