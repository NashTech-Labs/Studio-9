package orion.domain.service.job

import orion.domain.service.{ Enum, EnumDeserializer }

sealed trait JobStatus extends Enum
object JobStatus extends EnumDeserializer[JobStatus] {
  case object Submitted extends JobStatus { def serialize: String = "submitted" }
  case object Queued extends JobStatus { def serialize: String = "queued" }
  case object Running extends JobStatus { def serialize: String = "running" }
  case object Succeeded extends JobStatus { def serialize: String = "succeeded" }
  case object Cancelled extends JobStatus { def serialize: String = "cancelled" }
  case object Failed extends JobStatus { def serialize: String = "failed" }

  val All: Seq[JobStatus] = Seq(Submitted, Queued, Running, Succeeded, Cancelled, Failed)

}
