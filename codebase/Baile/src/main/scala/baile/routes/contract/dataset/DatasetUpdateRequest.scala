package baile.routes.contract.dataset

import play.api.libs.json.{ Json, Reads }

case class DatasetUpdateRequest(
  name: Option[String],
  description: Option[String]
)

object DatasetUpdateRequest {

  implicit val DatasetUpdateRequestReads: Reads[DatasetUpdateRequest] =
    Json.reads[DatasetUpdateRequest]

}
