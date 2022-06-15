package cortex.jobmaster.jobs.time

import java.util.Date

import cortex.TaskTimeInfo
import org.joda.time.format.DateTimeFormat
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.concurrent.duration._

class JobTimeInfoTest extends FlatSpec {
  private val formatter = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss")
  def getDate(dateTime: String): Date = new Date(formatter.parseDateTime(dateTime).toInstant.getMillis)

  "JobTimeInfo" should "calculate tasks queue time properly" in {
    val expectedQueuedTime = FiniteDuration(10 + 5, MINUTES)
    val tasksTimeInfo = Seq(
      TaskTimeInfo("task_id", getDate("23/01/2019 10:00:00"), Some(getDate("23/01/2019 10:10:00")), Some(getDate("23/01/2019 10:50:00"))),
      TaskTimeInfo("task_id2", getDate("23/01/2019 10:05:00"), Some(getDate("23/01/2019 10:10:00")), Some(getDate("23/01/2019 10:20:00"))),
      TaskTimeInfo("task_id3", getDate("23/01/2019 10:20:00"), Some(getDate("23/01/2019 10:55:00")), Some(getDate("23/01/2019 11:00:00"))),
      TaskTimeInfo("task_id4", getDate("23/01/2019 10:20:00"), Some(getDate("23/01/2019 10:55:00")), Some(getDate("23/01/2019 11:00:00")))
    )

    JobTimeInfo(tasksTimeInfo).jobTasksQueuedTime shouldBe expectedQueuedTime
  }

  it should "calculate tasks queue time properly if one of tasks starts and completes before another starts" in {
    val expectedQueuedTime = FiniteDuration(6 + 2, MINUTES)
    val tasksTimeInfo = Seq(
      TaskTimeInfo("task_id", getDate("23/01/2019 10:00:00"), Some(getDate("23/01/2019 10:10:00")), Some(getDate("23/01/2019 10:50:00"))),
      TaskTimeInfo("task_id2", getDate("23/01/2019 10:05:00"), Some(getDate("23/01/2019 10:06:00")), Some(getDate("23/01/2019 10:08:00")))
    )

    JobTimeInfo(tasksTimeInfo).jobTasksQueuedTime shouldBe expectedQueuedTime
  }

  it should "calculate tasks queue time properly if one of tasks completes before another starts" in {
    val expectedQueuedTime = FiniteDuration(2 + 4, MINUTES)
    val tasksTimeInfo = Seq(
      TaskTimeInfo("task_id", getDate("23/01/2019 10:05:00"), Some(getDate("23/01/2019 10:10:00")), Some(getDate("23/01/2019 10:50:00"))),
      TaskTimeInfo("task_id2", getDate("23/01/2019 10:02:00"), Some(getDate("23/01/2019 10:06:00")), Some(getDate("23/01/2019 10:08:00")))
    )

    JobTimeInfo(tasksTimeInfo).jobTasksQueuedTime shouldBe expectedQueuedTime
  }

  it should "calculate tasks queue time properly again" in {
    val expectedQueuedTime = FiniteDuration(10 + 20 + 5, MINUTES)
    val tasksTimeInfo = Seq(
      TaskTimeInfo("task_id", getDate("23/01/2019 10:00:00"), Some(getDate("23/01/2019 10:40:00")), Some(getDate("23/01/2019 10:50:00"))),
      TaskTimeInfo("task_id2", getDate("23/01/2019 10:05:00"), Some(getDate("23/01/2019 10:10:00")), Some(getDate("23/01/2019 10:20:00"))),
      TaskTimeInfo("task_id3", getDate("23/01/2019 10:20:00"), Some(getDate("23/01/2019 10:55:00")), Some(getDate("23/01/2019 11:00:00")))
    )

    JobTimeInfo(tasksTimeInfo).jobTasksQueuedTime shouldBe expectedQueuedTime
  }
}
