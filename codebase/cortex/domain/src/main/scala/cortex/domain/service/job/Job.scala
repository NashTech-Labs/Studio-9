package cortex.domain.service.job

import java.util.{ Date, UUID }

import cortex.api.job.message.{ TaskTimeInfo, TimeInfo }
import cortex.domain.service.{ DomainObject, Enum, EnumDeserializer, UUIDEntity }

import scala.concurrent.duration.FiniteDuration

case class SubmitJobData(
  id:        Option[UUID],
  owner:     UUID,
  jobType:   String,
  inputPath: String
) extends DomainObject

case class CreateJobData(
  id:          UUID,
  owner:       UUID,
  jobType:     String,
  status:      JobStatus,
  inputPath:   String,
  submittedAt: Date
) extends DomainObject

case class UpdateJobData(
  status:             Option[JobStatus]          = None,
  timeInfo:           Option[UpdateTimeInfo]     = None,
  tasksTimeInfo:      Option[Seq[TaskTimeInfo]]  = None,
  tasksQueuedTime:    Option[FiniteDuration]     = None,
  outputPath:         Option[String]             = None,
  cortexErrorDetails: Option[CortexErrorDetails] = None
) extends DomainObject

case class UpdateTimeInfo(
  submittedAt: Option[Date] = None,
  startedAt:   Option[Date] = None,
  completedAt: Option[Date] = None
) extends DomainObject

case class JobEntity(
  id:                 UUID,
  owner:              UUID,
  jobType:            String,
  status:             JobStatus,
  inputPath:          String,
  timeInfo:           TimeInfo,
  tasksTimeInfo:      Seq[TaskTimeInfo],
  tasksQueuedTime:    Option[FiniteDuration],
  outputPath:         Option[String]             = None,
  cortexErrorDetails: Option[CortexErrorDetails] = None
) extends UUIDEntity

sealed trait JobStatus extends Enum
object JobStatus extends EnumDeserializer[JobStatus] {
  case object Submitted extends JobStatus { def serialize: String = "submitted" }
  case object Queued extends JobStatus { def serialize: String = "queued" }
  case object Running extends JobStatus { def serialize: String = "running" }
  case object Completed extends JobStatus { def serialize: String = "completed" }
  case object Cancelled extends JobStatus { def serialize: String = "cancelled" }
  case object Failed extends JobStatus { def serialize: String = "failed" }

  val All: Seq[JobStatus] = Seq(Submitted, Queued, Running, Completed, Cancelled, Failed)
}

case class JobSearchCriteria(
  owner:   Option[UUID]      = None,
  jobType: Option[String]    = None,
  status:  Option[JobStatus] = None
) extends DomainObject

case class HeartbeatData(
  jobId:                  UUID,
  created:                Date,
  currentProgress:        Double,
  estimatedTimeRemaining: Option[FiniteDuration]
) extends DomainObject

case class HeartbeatEntity(
  id:                     UUID,
  jobId:                  UUID,
  created:                Date,
  currentProgress:        Double,
  estimatedTimeRemaining: Option[FiniteDuration]
) extends UUIDEntity

case class JobStatusData(
  status:                 JobStatus,
  currentProgress:        Option[Double],
  estimatedTimeRemaining: Option[FiniteDuration],
  cortexErrorDetails:     Option[CortexErrorDetails]
) extends DomainObject

case class CortexErrorDetails(errorCode: String, errorMessages: String, errorDetails: Map[String, String])
