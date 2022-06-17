package baile.routes.contract.usermanagement

import java.util.UUID

import play.api.libs.json.{ Json, Reads }

case class EmailConfirmationRequest(
  orgId: String,
  userId: UUID,
  activationCode: UUID
)

object EmailConfirmationRequest {
  implicit val EmailConfirmationRequestReads: Reads[EmailConfirmationRequest] = Json.reads[EmailConfirmationRequest]
}
