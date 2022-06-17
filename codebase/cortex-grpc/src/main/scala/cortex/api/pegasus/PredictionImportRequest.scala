package cortex.api.pegasus

import java.time.ZonedDateTime

import cortex.api.RMQBaseMessage
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

/**
  * @param jobId Job id which is traversed through all services
  * @param streamId Stream of an online prediction task
  * @param albumId Album which is used while prediction
  * @param owner Stream owner
  * @param createdAt Timestamp of a prediction request initialization
  * @param createdBy Right now it can be only "Taurus", later we also may want saving "general" predictions,
  *                  in this case it will be "Baile"
  * @param s3PredictionCsvPath Path to csv which contains online prediction results
  */
case class PredictionImportRequest(
  jobId: String,
  streamId: String,
  albumId: String,
  owner: String,
  createdAt: ZonedDateTime,
  createdBy: CreatedBy,
  s3PredictionCsvPath: String
) extends RMQBaseMessage {
  override val id = jobId
}

object PredictionImportRequest {
  object CreatedByFormats extends CustomSerializer[CreatedBy](_ => (
    {
      case JString("Taurus") => CreatedBy.Taurus
    },
    {
      case CreatedBy.Taurus => JString("Taurus")
    }
  ))
}
