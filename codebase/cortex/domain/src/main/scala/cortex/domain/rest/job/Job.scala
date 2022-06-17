package cortex.domain.rest.job

import java.util.{ Date, UUID }

import cortex.domain.rest.HttpContract
import cortex.domain.service.job.JobStatus

import scala.concurrent.duration.FiniteDuration

case class SubmitJobDataContract(
  id:        Option[UUID],
  owner:     UUID,
  jobType:   String,
  inputPath: String
) extends HttpContract

case class JobEntityContract(
  id:              UUID,
  owner:           UUID,
  jobType:         String,
  status:          JobStatus,
  inputPath:       String,
  timeInfo:        TimeInfoContract,
  tasksTimeInfo:   Seq[TaskTimeInfoContract],
  tasksQueuedTime: Option[FiniteDuration],
  outputPath:      Option[String]            = None
) extends HttpContract

case class JobSearchCriteriaContract(
  owner:   Option[UUID],
  jobType: Option[String],
  status:  Option[JobStatus]
) extends HttpContract

case class JobStatusDataContract(
  status:                 JobStatus,
  currentProgress:        Option[Double],
  estimatedTimeRemaining: Option[FiniteDuration],
  cortexErrorDetails:     Option[CortexErrorDetailsContract]
) extends HttpContract

case class TaskTimeInfoContract(taskName: String, timeInfo: TimeInfoContract)

case class TimeInfoContract(submittedAt: Date, startedAt: Option[Date], completedAt: Option[Date])

case class CortexErrorDetailsContract(errorCode: String, errorMessages: String, errorDetails: Map[String, String])
