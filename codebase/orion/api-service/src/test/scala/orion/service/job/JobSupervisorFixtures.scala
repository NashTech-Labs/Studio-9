package orion.service.job

import java.util.Date

import com.typesafe.config.ConfigFactory
import mesosphere.marathon.client.model.v2.App
import cortex.api.job.message.{ TaskTimeInfo, TimeInfo }

import scala.concurrent.duration.{ FiniteDuration, _ }

object JobSupervisorFixtures {

  import orion.common.service.DateImplicits._

  val jobType = "TRAIN"
  val inputPath = "some/input/path"
  val outputPath = "some/output/path"
  val tasksTimeInfo: Seq[TaskTimeInfo] = Seq(
    TaskTimeInfo("task1", TimeInfo(new Date().withoutMillis(), Some(new Date().withoutMillis()), None)),
    TaskTimeInfo("task2", TimeInfo(new Date().withoutMillis(), Some(new Date().withoutMillis()), None))
  )
  val tasksQueuedTime = 20 minutes
  val completedAt: Date = new Date().withoutMillis()
  val jobMasterApp = new App()
  val created: Date = new Date().withoutMillis()
  val currentProgress = 0.1D
  val estimatedTimeRemaining: FiniteDuration = 2 hours
  val messagePublishingRetries: Int = new JobSupervisorSettings(ConfigFactory.load()).messagePublishingRetries
  val decreasedRetries: Int = messagePublishingRetries - 1
  val unrecoverableError = new Exception("BOOM!")
}
