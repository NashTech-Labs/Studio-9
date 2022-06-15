package cortex.task.tabular_data

import cortex.JsonSupport.SnakeJson
import play.api.libs.json.OWrites

case class Table(
    schema: String,
    name:   String
)

object Table {

  implicit val TableWrites: OWrites[Table] = SnakeJson.writes[Table]

}
