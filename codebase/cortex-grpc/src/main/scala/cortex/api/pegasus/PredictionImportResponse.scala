package cortex.api.pegasus

import cortex.api.RMQBaseMessage
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

/**
  * @param jobId Job id which is traversed through all services
  * @param pegasusJobStatus Prediction importing status - will be sent when
  *                         the overall prediction importing process will be finished
  */
case class PredictionImportResponse(
  jobId: String,
  pegasusJobStatus: PegasusJobStatus
) extends RMQBaseMessage {
  override val id = jobId
}

object PredictionImportResponse {
  object PegasusJobStatusFormats extends CustomSerializer[PegasusJobStatus](_ => (
    {
      case JString("Succeed") => PegasusJobStatus.Succeed
      case JString("Failed") => PegasusJobStatus.Failed
    },
    {
      case PegasusJobStatus.Succeed => JString("Succeed")
      case PegasusJobStatus.Failed => JString("Failed")
    }
  ))
}
