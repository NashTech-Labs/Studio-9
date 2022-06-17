package baile.routes.contract.tabular.model

import play.api.libs.json.{ Json, Reads }

case class TabularModelCreateRequest(
  name: String,
  input: String,
  holdOutInput: Option[String],
  outOfTimeInput: Option[String],
  samplingWeightColumn: Option[String],
  responseColumn: ModelColumn,
  predictorColumns: Seq[ModelColumn],
  description: Option[String]
)

object TabularModelCreateRequest {

  implicit val TabularModelCreateRequestReads: Reads[TabularModelCreateRequest] = Json.reads[TabularModelCreateRequest]

}
