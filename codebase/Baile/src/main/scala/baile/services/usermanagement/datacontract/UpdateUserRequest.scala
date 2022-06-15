package baile.services.usermanagement.datacontract

import play.api.libs.json.{ Json, OWrites }

case class UpdateUserRequest(
  username: Option[String],
  email: Option[String],
  password: Option[String],
  firstName: Option[String],
  lastName: Option[String],
  groupIds: Option[Set[String]]
)

object UpdateUserRequest {
  implicit val UpdateUserRequestWrites: OWrites[UpdateUserRequest] = Json.writes[UpdateUserRequest]
}

