package baile.services.usermanagement.datacontract

import play.api.libs.json.{ Json, Reads }

case class ConfirmUserResponse(message: String)

object ConfirmUserResponse {
  implicit val ConfirmUserResponseReads: Reads[ConfirmUserResponse] =
    Json.reads[ConfirmUserResponse]
}
