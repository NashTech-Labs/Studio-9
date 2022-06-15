package baile.domain.job

import java.util.UUID

import baile.services.cortex.datacontract.CortexErrorDetails

import scala.concurrent.duration.FiniteDuration

case class CortexJobProgress(
  jobId: UUID,
  status: CortexJobStatus,
  progress: Option[Double],
  estimatedTimeRemaining: Option[FiniteDuration],
  cortexErrorDetails: Option[CortexErrorDetails]
)
