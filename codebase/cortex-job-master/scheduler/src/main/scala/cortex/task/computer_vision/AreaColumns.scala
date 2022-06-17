package cortex.task.computer_vision

import cortex.JsonSupport.SnakeJson
import play.api.libs.json.Reads

case class AreaColumns(
    xMin: String,
    yMin: String,
    xMax: String,
    yMax: String
)

object AreaColumns {
  implicit val areaColumnsReads: Reads[AreaColumns] = SnakeJson.reads[AreaColumns]
}
