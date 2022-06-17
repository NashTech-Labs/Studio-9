package cortex.api

import play.api.libs.json.{ JsValue, Json, OFormat }

case class ErrorResponse(
    code: String,
    description: String,
    details: Seq[JsValue]
)

object ErrorResponse {

  implicit val format: OFormat[ErrorResponse] = Json.format[ErrorResponse]

  def apply(error: Error, details: Seq[JsValue] = Seq.empty): ErrorResponse =
    apply(error.code, error.description, details)

}
