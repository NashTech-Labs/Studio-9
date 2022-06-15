package baile.services.usermanagement.datacontract

import play.api.libs.json.{ Json, OWrites }

case class UserDeactivationRequest(
  sendNotificationEmail: Boolean
)

object UserDeactivationRequest {
  implicit val UserDeactivationRequestWrites: OWrites[UserDeactivationRequest] = Json.writes[UserDeactivationRequest]
}
