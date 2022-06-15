package cortex.api.gemini

import play.api.libs.json.{ Json, OFormat }

case class JupyterSessionRequest(
    userAccessToken: String,
    awsRegion: String,
    awsAccessKey: String,
    awsSecretKey: String,
    awsSessionToken: String,
    bucketName: String,
    projectPath: String,
    nodeParams: JupyterNodeParamsRequest
)

object JupyterSessionRequest {

  implicit val format: OFormat[JupyterSessionRequest] =
    Json.format[JupyterSessionRequest]

}
