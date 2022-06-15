package cortex.task.computer_vision

import cortex.JsonSupport.SnakeJson
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.common.ClassReference
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json.{ Reads, Writes }

object CustomModelParams {

  case class TagArea(top: Int, left: Int, height: Int, width: Int)

  case class Tag(label: String, area: Option[TagArea], confidence: Option[Double] = None)

  case class ConfusionMatrixCell(actualLabelIndex: Option[Int], predictedLabelIndex: Option[Int], value: Int)

  case class ConfusionMatrix(confusionMatrixCells: Seq[ConfusionMatrixCell], labels: Seq[String])

  case class PredictionResult(filename: String, tags: Seq[Tag])

  case class ScoreTaskParams(
      modelId:        String,
      modelsBasePath: String,
      albumPath:      String,
      tags:           Seq[Seq[Tag]],
      imagePaths:     Seq[String],
      classReference: ClassReference,
      outputS3Params: S3AccessParams,
      action:         String         = "score"
  ) extends TaskParams

  case class ScoreTaskResult(
      predictions:     Seq[PredictionResult],
      mAP:             Option[Double],
      confusionMatrix: ConfusionMatrix,
      dataFetchTime:   Long,
      loadModelTime:   Long,
      scoreTime:       Long
  ) extends TaskResult

  case class PredictTaskParams(
      modelId:           String,
      modelsBasePath:    String,
      albumPath:         String,
      imagePaths:        Seq[String],
      referenceIds:      Seq[Option[String]],
      displayNames:      Option[Seq[Option[String]]],
      classReference:    ClassReference,
      outputS3Params:    S3AccessParams,
      outputTableS3Path: Option[String],
      action:            String                      = "predict"
  ) extends TaskParams

  case class PredictTaskResult(
      predictions:     Seq[PredictionResult],
      predictionTable: Option[PredictionTable],
      dataFetchTime:   Long,
      loadModelTime:   Long,
      predictionTime:  Long
  ) extends TaskResult

  implicit val tagAreaReads: Reads[TagArea] = SnakeJson.reads[TagArea]
  implicit val tagAreaWrites: Writes[TagArea] = SnakeJson.writes[TagArea]
  implicit val tagReads: Reads[Tag] = SnakeJson.reads[Tag]
  implicit val tagWrites: Writes[Tag] = SnakeJson.writes[Tag]
  implicit val predictionResultReads: Reads[PredictionResult] = SnakeJson.reads[PredictionResult]
  implicit val confusionMatrixCellReads: Reads[ConfusionMatrixCell] = SnakeJson.reads[ConfusionMatrixCell]
  implicit val confusionMatrixReads: Reads[ConfusionMatrix] = SnakeJson.reads[ConfusionMatrix]

  implicit val scoreTaskParamsWrites: Writes[ScoreTaskParams] = SnakeJson.writes[ScoreTaskParams]
  implicit val scoreTaskResultReads: Reads[ScoreTaskResult] = SnakeJson.reads[ScoreTaskResult]

  implicit val predictTaskParamsWrites: Writes[PredictTaskParams] = SnakeJson.writes[PredictTaskParams]
  implicit val predictTaskResultReads: Reads[PredictTaskResult] = SnakeJson.reads[PredictTaskResult]

}
