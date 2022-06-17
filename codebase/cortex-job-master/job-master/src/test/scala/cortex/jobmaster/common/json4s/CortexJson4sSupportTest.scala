package cortex.jobmaster.common.json4s

import java.time.ZonedDateTime
import java.util.{ Date, UUID }

import cortex.api.job.message.{ JobMessage, JobMessageMeta, JobResultSuccess, TaskTimeInfo, TimeInfo }
import org.json4s.native.Serialization.write
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import scala.concurrent.duration._
import org.json4s.jackson.JsonMethods._

class CortexJson4sSupportTest extends FlatSpec with CortexJson4sSupport {
  "JobResutSuccess" should "be serialized to json string properly" in {
    val jobUuid = UUID.fromString("548c0dca-8d39-4056-97ab-4a22daea457e")

    val expectedString =
      """{"meta":{"jobId":"548c0dca-8d39-4056-97ab-4a22daea457e","jobType":"TRAIN"},""" +
        """"payload":{"type":"JobResultSuccess","completedAt":"2019-01-21T03:57:47Z",""" +
        """"tasksTimeInfo":[{"taskName":"taskName","timeInfo":{"submittedAt":"2019-01-21T03:54:47Z"}}],""" +
        """"tasksQueuedTime":{"length":2,"unit":"SECONDS"},"outputPath":"hdfs://some/input/path"}}"""

    val jobMessage = JobMessage(
      JobMessageMeta(jobUuid, Some("TRAIN")),
      JobResultSuccess(
        Date.from(ZonedDateTime.parse("2019-01-21T03:57:47Z").toInstant),
        Seq(TaskTimeInfo("taskName", TimeInfo(Date.from(ZonedDateTime.parse("2019-01-21T03:54:47Z").toInstant), None, None))),
        2 seconds,
        "hdfs://some/input/path"
      )
    )

    write(jobMessage) shouldBe expectedString
  }

  it should "be deserialized to object properly" in {
    val jobUuid = UUID.fromString("548c0dca-8d39-4056-97ab-4a22daea457e")

    val expectedJobMessage = JobMessage(
      JobMessageMeta(jobUuid, Some("TRAIN")),
      JobResultSuccess(
        Date.from(ZonedDateTime.parse("2019-01-21T03:57:47Z").toInstant),
        Seq(TaskTimeInfo("taskName", TimeInfo(Date.from(ZonedDateTime.parse("2019-01-21T03:54:47Z").toInstant), None, None))),
        2 seconds,
        "hdfs://some/input/path"
      )
    )

    val serializedJobMessage =
      """{"meta":{"jobId":"548c0dca-8d39-4056-97ab-4a22daea457e","jobType":"TRAIN"},""" +
        """"payload":{"type":"JobResultSuccess","completedAt":"2019-01-21T03:57:47Z",""" +
        """"tasksTimeInfo":[{"taskName":"taskName","timeInfo":{"submittedAt":"2019-01-21T03:54:47Z"}}],""" +
        """"tasksQueuedTime":{"length":2,"unit":"SECONDS"},"outputPath":"hdfs://some/input/path"}}"""

    parse(serializedJobMessage).extract[JobMessage] shouldBe expectedJobMessage
  }
}
