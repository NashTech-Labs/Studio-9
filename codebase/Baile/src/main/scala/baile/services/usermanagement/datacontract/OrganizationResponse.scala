package baile.services.usermanagement.datacontract

import play.api.libs.json.{ Json, Reads }

case class OrganizationResponse(
  id: String,
  name: String
)

object OrganizationResponse {

  implicit val OrganizationResponseReads: Reads[OrganizationResponse] = Json.reads[OrganizationResponse]

}
