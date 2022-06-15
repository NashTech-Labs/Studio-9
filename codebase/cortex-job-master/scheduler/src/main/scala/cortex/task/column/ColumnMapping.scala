package cortex.task.column

import cortex.JsonSupport.SnakeJson
import play.api.libs.json.Format

case class ColumnMapping(
    name:         String,
    displayName:  String,
    dataType:     ColumnDataType,
    variableType: ColumnVariableType
)

object ColumnMapping {

  implicit val columnMappingReads: Format[ColumnMapping] = SnakeJson.format[ColumnMapping]

}
