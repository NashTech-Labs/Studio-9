package baile.routes.contract.tabular.model

import play.api.libs.json.{ Json, Reads }

case class TabularModelCloneOrSaveRequest(
  name: String,
  description: Option[String]
)

object TabularModelCloneOrSaveRequest {
  implicit val TabularModelCloneOrSaveRequest: Reads[TabularModelCloneOrSaveRequest] =
    Json.reads[TabularModelCloneOrSaveRequest]
}
