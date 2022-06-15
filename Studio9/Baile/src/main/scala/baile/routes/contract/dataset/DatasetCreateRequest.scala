package baile.routes.contract.dataset

import play.api.libs.json.{ Json, Reads }

case class DatasetCreateRequest(
  name: Option[String],
  description: Option[String]
)

object DatasetCreateRequest {

  implicit val DatasetCreateRequestReads: Reads[DatasetCreateRequest] =
    Json.reads[DatasetCreateRequest]

}
