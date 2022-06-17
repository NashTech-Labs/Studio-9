package cortex.task.transform.common

import cortex.JsonSupport.SnakeJson
import cortex.task.column.ColumnDataType
import play.api.libs.json._

case class Column(
    name:   String,
    `type`: ColumnDataType
)

object Column {
  implicit val ColumnWrites: OWrites[Column] = SnakeJson.writes[Column]
}
