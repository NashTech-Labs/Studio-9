package baile.routes.contract.table

import baile.domain.table.TableRow
import play.api.libs.json.{ Json, Writes }

case class ListDatasetRowResponse(
  values: Seq[ListDatasetValueResponse]
)

object ListDatasetRowResponse {

  def fromDomain(row: TableRow): ListDatasetRowResponse = {
    ListDatasetRowResponse(row.values.map(ListDatasetValueResponse.fromDomain))
  }

  implicit val ListDatasetRowResponseWrites: Writes[ListDatasetRowResponse] = Writes[ListDatasetRowResponse] {
    case ListDatasetRowResponse(values) => Json.toJson(values)
  }

}
