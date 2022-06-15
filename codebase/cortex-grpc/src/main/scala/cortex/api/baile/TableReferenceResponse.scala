package cortex.api.baile

import cortex.api.Error
import play.api.libs.json.{ Json, OFormat }

case class TableReferenceResponse(
    tableName: String,
    schema: String
)

object TableReferenceResponse {

  implicit val format: OFormat[TableReferenceResponse] = Json.format[TableReferenceResponse]

  object Errors {
    val Table1 = Error("TABLE-1", "Table does not exist")
    val Table2 = Error("TABLE-2", "Access denied")
  }

}
