package baile.services.usermanagement.datacontract

import play.api.libs.json._

case class ErrorResponse(error: String, error_description: Option[String])

object ErrorResponse {

  implicit val ErrorResponseReads: Reads[ErrorResponse] =
    Json.reads[ErrorResponse] orElse (__ \ "message").read[String].map(ErrorResponse(_, None))

}
