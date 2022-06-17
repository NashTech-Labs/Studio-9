package aries.domain.service.job

import aries.domain.service.{ Enum, EnumDeserializer }

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