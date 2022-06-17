package cortex.task.tabular_data.mvh

import cortex.JsonSupport.SnakeJson
import cortex.task.StorageAccessParams
import cortex.task.tabular_data.ModelReference
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json.{ OWrites, Reads }

object MVHParams {

  /**
   * Train
   */

  case class MVHTrainParams(
      trainInputPaths:       Seq[String],
      numericalPredictors:   Seq[String],
      categoricalPredictors: Seq[String],
      storageAccessParams:   StorageAccessParams,
      modelsBasePath:        String,
      action:                String              = "train"
  ) extends TaskParams

  case class MVHTrainResult(
      taskId:         String,
      modelReference: ModelReference
  ) extends TaskResult

  implicit val mvhTrainPredictParamsWrites: OWrites[MVHTrainParams] = SnakeJson.writes[MVHTrainParams]
  implicit val mvhTrainPredictResultReads: Reads[MVHTrainResult] = SnakeJson.reads[MVHTrainResult]
}
