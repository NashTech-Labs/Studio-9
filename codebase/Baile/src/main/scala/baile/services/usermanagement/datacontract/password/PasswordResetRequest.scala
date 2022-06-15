package baile.services.usermanagement.datacontract.password

import play.api.libs.json.{ Json, OWrites }

case class PasswordResetRequest(email: String, appId: Option[String])

object PasswordResetRequest {

  implicit val PasswordResetRequestWrites: OWrites[PasswordResetRequest] = Json.writes[PasswordResetRequest]

}
