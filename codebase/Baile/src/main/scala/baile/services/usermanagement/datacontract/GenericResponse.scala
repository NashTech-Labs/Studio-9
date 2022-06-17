package baile.services.usermanagement.datacontract

import play.api.libs.json.{ Json, Reads }

case class GenericResponse(message: String, id: String)

object GenericResponse {
  implicit val GenericResponseReads: Reads[GenericResponse] = Json.reads[GenericResponse]
}
