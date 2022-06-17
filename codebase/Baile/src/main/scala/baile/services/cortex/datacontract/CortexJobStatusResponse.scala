package baile.services.cortex.datacontract

import baile.domain.job.CortexJobStatus
import baile.utils.json.CommonFormats.FiniteDurationFormat
import play.api.libs.json._

import scala.concurrent.duration.FiniteDuration

private[cortex] case class CortexJobStatusResponse(
  status: CortexJobStatus,
  currentProgress: Option[Double],
  estimatedTimeRemaining: Option[FiniteDuration],
  cortexErrorDetails: Option[CortexErrorDetails]
)

private[cortex] object CortexJobStatusResponse {

  implicit val CortexJobStatusResponseReads: Reads[CortexJobStatusResponse] = Json.reads[CortexJobStatusResponse]

}
