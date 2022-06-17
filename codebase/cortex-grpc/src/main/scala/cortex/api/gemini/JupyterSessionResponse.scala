package cortex.api.gemini

import java.time.Instant

import play.api.libs.json.{ Json, OFormat }

case class JupyterSessionResponse(
    id: String,
    token: String,
    url: String,
    status: SessionStatus,
    startedAt: Instant
)

object JupyterSessionResponse {

  implicit val format: OFormat[JupyterSessionResponse] =
    Json.format[JupyterSessionResponse]

}
