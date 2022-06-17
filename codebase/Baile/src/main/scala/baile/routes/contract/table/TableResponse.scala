package baile.routes.contract.table

import java.time.Instant
import java.util.UUID

import baile.daocommons.WithId
import baile.domain.table.{ Table, TableStatus, TableType }
import play.api.libs.json.{ Json, OWrites }

case class TableResponse(
  id: String,
  ownerId: UUID,
  datasetId: String,
  name: String,
  status: TableStatus,
  datasetType: TableType,
  created: Instant,
  updated: Instant,
  description: Option[String],
  inLibrary: Boolean
)

object TableResponse {
  implicit val TableResponseWrites: OWrites[TableResponse] = Json.writes[TableResponse]

  def fromDomain(in: WithId[Table]): TableResponse = {
    in match {
      case WithId(table, id) => TableResponse(
        id = id,
        ownerId = table.ownerId,
        datasetId = table.databaseId,
        name = table.name,
        status = table.status,
        datasetType = table.`type`,
        created = table.created,
        updated = table.updated,
        description = table.description,
        inLibrary = table.inLibrary
      )
    }
  }
}
