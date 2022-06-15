package baile.services.cortex.datacontract

import java.util.UUID

import baile.domain.job.CortexJobStatus
import baile.utils.json.CommonFormats.FiniteDurationFormat
import play.api.libs.json.{ Json, Reads }

import scala.concurrent.duration.FiniteDuration

private[cortex] case class CortexJobResponse(
  id: UUID,
  owner: UUID,
  jobType: String,
  status: CortexJobStatus,
  inputPath: String,
  timeInfo: CortexTimeInfoResponse,
  tasksTimeInfo: Seq[CortexTaskTimeInfoResponse],
  tasksQueuedTime: Option[FiniteDuration],
  outputPath: Option[String]
)

private[cortex] object CortexJobResponse {

  implicit val CortexJobResponseReads: Reads[CortexJobResponse] = Json.reads[CortexJobResponse]

}
