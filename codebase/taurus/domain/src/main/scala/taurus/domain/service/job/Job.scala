package taurus.domain.service.job

import java.util.{ Date, UUID }

import taurus.domain.service.{ DomainObject, Enum, EnumDeserializer, UUIDEntity }

import scala.concurrent.duration.FiniteDuration

case class SubmitJobData(
  id:        Option[UUID],
  owner:     UUID,
  jobType:   String,
  inputPath: String
) extends DomainObject

case class JobEntity(
  id:          UUID,
  created:     Date,
  owner:       UUID,
  jobType:     String,
  status:      JobStatus,
  inputPath:   String,
  startedAt:   Option[Date]   = None,
  completedAt: Option[Date]   = None,
  outputPath:  Option[String] = None
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

case class JobStatusData(
  status:                 JobStatus,
  currentProgress:        Option[Double],
  estimatedTimeRemaining: Option[FiniteDuration]
) extends DomainObject
