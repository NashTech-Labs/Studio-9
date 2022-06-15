package cortex.task.computer_vision

import play.api.libs.json.Reads
import cortex.JsonSupport._

case class ProbabilityClassColumn(className: String, columnName: String)

object ProbabilityClassColumn {
  implicit val probabilityClassColumnReads: Reads[ProbabilityClassColumn] = SnakeJson.reads[ProbabilityClassColumn]
}
