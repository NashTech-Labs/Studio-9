package baile.services.usermanagement.datacontract.password

import play.api.libs.json.{ Json, OWrites }

case class UpdateForgottenPasswordRequest(email: String, secretCode: String, newPassword: String)

object UpdateForgottenPasswordRequest {

  implicit val UpdateForgottenPasswordRequestWrites: OWrites[UpdateForgottenPasswordRequest] =
    Json.writes[UpdateForgottenPasswordRequest]

}
