package cortex.task.computer_vision

import cortex.JsonSupport.SnakeJson
import play.api.libs.json.Reads

case class PredictionTable(
    filenameColumn:     String,
    probabilityColumns: Seq[ProbabilityClassColumn],
    areaColumns:        Option[AreaColumns],
    referenceIdColumn:  String
)

object PredictionTable {
  implicit val predictionTableReads: Reads[PredictionTable] = SnakeJson.reads[PredictionTable]
}
