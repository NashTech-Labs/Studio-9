package baile.routes.contract.dataset

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.dataset.{ Dataset, DatasetStatus }
import play.api.libs.json.{ Json, OWrites }

case class DatasetResponse(
  id: String,
  name: String,
  created: Instant,
  updated: Instant,
  ownerId: String,
  description: Option[String],
  status: DatasetStatus
)

object DatasetResponse {

  implicit val DatasetResponseWrites: OWrites[DatasetResponse] = Json.writes[DatasetResponse]

  def fromDomain(in: WithId[Dataset]): DatasetResponse = in match {
    case WithId(dataset, id) => DatasetResponse(
      id = id,
      name = dataset.name,
      created = dataset.created,
      updated = dataset.updated,
      ownerId = dataset.ownerId.toString,
      description = dataset.description,
      status = dataset.status
    )
  }

}
