package cortex.api.gemini
import play.api.libs.json.{ Json, OFormat }

case class JupyterNodeParamsRequest(
    numberOfCpus: Option[Double],
    numberOfGpus: Option[Double]
)

object JupyterNodeParamsRequest {
  implicit val format: OFormat[JupyterNodeParamsRequest] = Json.format[JupyterNodeParamsRequest]
}
