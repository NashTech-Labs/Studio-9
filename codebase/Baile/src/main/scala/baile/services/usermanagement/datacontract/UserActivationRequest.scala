package baile.services.usermanagement.datacontract

import play.api.libs.json.{ Json, OWrites }

case class UserActivationRequest(
  requireEmailConfirmation: Boolean
)

object UserActivationRequest {
  implicit val UserActivationRequestWrites: OWrites[UserActivationRequest] = Json.writes[UserActivationRequest]
}
