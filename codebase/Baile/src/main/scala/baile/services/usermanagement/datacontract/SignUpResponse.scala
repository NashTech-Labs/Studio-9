package baile.services.usermanagement.datacontract

import java.util.UUID

import play.api.libs.json.{ Json, Reads }

case class SignUpResponse(id: UUID, message: String)

object SignUpResponse {

  implicit val SignUpResponseReads: Reads[SignUpResponse] = Json.format[SignUpResponse]

}
