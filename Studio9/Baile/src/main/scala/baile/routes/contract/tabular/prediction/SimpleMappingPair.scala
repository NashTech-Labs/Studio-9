package baile.routes.contract.tabular.prediction

import play.api.libs.json.{ Json, Format }

case class SimpleMappingPair(
  sourceColumn: String,
  mappedColumn: String
)

object SimpleMappingPair {
  implicit val ColumnMappingPairFormat: Format[SimpleMappingPair] = Json.format[SimpleMappingPair]
}
