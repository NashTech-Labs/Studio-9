package baile.routes.contract.table

import play.api.libs.json.{ Json, Reads }

case class TableCloneOrSaveRequest(name: String)

object TableCloneOrSaveRequest {
  implicit val TableCloneRequestReads: Reads[TableCloneOrSaveRequest] = Json.reads[TableCloneOrSaveRequest]
}
