package baile.services.usermanagement.datacontract.password

import play.api.libs.json.{ Json, OWrites }

case class UpdatePasswordRequest(oldPassword: String, newPassword: String)

object UpdatePasswordRequest {

  implicit val UpdatePasswordRequestWrites: OWrites[UpdatePasswordRequest] =
    Json.format[UpdatePasswordRequest]

}
