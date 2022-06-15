package baile.services.usermanagement.datacontract

import play.api.libs.json.{ Json, OWrites }

case class ReSendActivationLinkRequest(email: String)

object ReSendActivationLinkRequest {

  implicit val ReSendActivationLinkRequestWrites: OWrites[ReSendActivationLinkRequest] =
    Json.writes[ReSendActivationLinkRequest]

}
