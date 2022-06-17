package baile.routes.contract.pipeline.category

import baile.domain.pipeline.category.Category
import play.api.libs.json.{ Json, OWrites }

case class CategoryResponse(
  id: String,
  name: String,
  icon: String
)

object CategoryResponse {

  def fromDomain(in: Category): CategoryResponse = CategoryResponse(
    id = in.id,
    name = in.name,
    icon = in.icon
  )

  implicit val CategoryResponseWrites: OWrites[CategoryResponse] = Json.writes[CategoryResponse]

}
