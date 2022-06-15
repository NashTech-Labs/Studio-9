package cortex.task.computer_vision

import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.computer_vision.AutoAugmentation.{ AutoAugmentationParams, AutoAugmentationResult }
import cortex.JsonSupport._
import cortex.task.common.ClassReference
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json._

object ClassificationParams {

  case class PredictionResult(filename: String, label: String, confidence: Double)

  case class ConfusionMatrixCell(actualLabelIndex: Int, predictedLabelIndex: Int, value: Int)

  case class ConfusionMatrix(confusionMatrixCells: Seq[ConfusionMatrixCell], labels: Seq[String])

  /**
   * Train
   */

  case class CVTrainTaskParams(
      albumPath:                      String,
      imagePaths:                     Seq[String],
      labels:                         Seq[String],
      displayNames:                   Option[Seq[Option[String]]],
      referenceIds:                   Seq[Option[String]],
      modelsBasePath:                 String,
      featureExtractorClassReference: ClassReference,
      tuneFeatureExtractor:           Boolean,
      classReference:                 ClassReference,
      outputS3Params:                 S3AccessParams,
      augmentationParams:             Option[AutoAugmentationParams],
      outputTableS3Path:              Option[String],
      featureExtractorParameters:     Map[String, ParameterValue]    = Map.empty,
      modelParameters:                Map[String, ParameterValue]    = Map.empty,
      featureExtractorId:             Option[String]                 = None,
      action:                         String                         = "train",
      testMode:                       Boolean                        = false
  ) extends TaskParams {
    assert(labels.length == imagePaths.length, "Lengths don't match")
    assert(referenceIds.length == imagePaths.length, "Lengths don't match")
    assert(displayNames.forall(_.length == imagePaths.length), "Lengths don't match")
  }

  case class CVTrainTaskResult(
      modelId:                  String,
      featureExtractorId:       String,
      predictions:              Seq[PredictionResult],
      confusionMatrix:          ConfusionMatrix,
      augmentationResult:       Option[AutoAugmentationResult],
      dataFetchTime:            Long,
      trainingTime:             Long,
      saveModelTime:            Long,
      saveFeatureExtractorTime: Long,
      predictionTime:           Long,
      pipelineTimings:          Map[String, Long],
      predictionTable:          Option[PredictionTable]
  ) extends TaskResult

  /**
   * Score
   */

  case class CVScoreTaskParams(
      imagePaths:                     Seq[String],
      albumPath:                      String,
      modelId:                        String,
      featureExtractorClassReference: ClassReference,
      classReference:                 ClassReference,
      labels:                         Seq[String],
      displayNames:                   Option[Seq[Option[String]]],
      referenceIds:                   Seq[Option[String]],
      modelsBasePath:                 String,
      outputS3Params:                 S3AccessParams,
      outputTableS3Path:              Option[String],
      action:                         String                      = "score"
  ) extends TaskParams {
    assert(labels.length == imagePaths.length, "Lengths don't match")
    assert(outputTableS3Path.isEmpty || referenceIds.length == imagePaths.length, "Lengths don't match")
    assert(displayNames.forall(_.length == imagePaths.length), "Lengths don't match")
  }

  case class CVScoreTaskResult(
      predictions:     Seq[PredictionResult],
      confusionMatrix: ConfusionMatrix,
      dataFetchTime:   Long,
      loadModelTime:   Long,
      scoreTime:       Long,
      pipelineTimings: Map[String, Long],
      predictionTable: Option[PredictionTable]
  ) extends TaskResult

  /**
   * Predict
   */

  case class CVPredictTaskParams(
      imagePaths:                     Seq[String],
      albumPath:                      String,
      modelId:                        String,
      featureExtractorClassReference: ClassReference,
      classReference:                 ClassReference,
      displayNames:                   Option[Seq[Option[String]]],
      referenceIds:                   Seq[Option[String]],
      modelsBasePath:                 String,
      outputS3Params:                 S3AccessParams,
      outputTableS3Path:              Option[String],
      action:                         String                      = "predict"
  ) extends TaskParams {
    assert(outputTableS3Path.isEmpty || referenceIds.length == imagePaths.length, "Lengths don't match")
    assert(displayNames.forall(_.length == imagePaths.length), "Lengths don't match")
  }

  case class CVPredictTaskResult(
      predictions:     Seq[PredictionResult],
      dataFetchTime:   Long,
      loadModelTime:   Long,
      predictionTime:  Long,
      pipelineTimings: Map[String, Long],
      predictionTable: Option[PredictionTable]
  ) extends TaskResult

  private implicit val predictionResultReads: Reads[PredictionResult] = SnakeJson.reads[PredictionResult]
  private implicit val confusionMatrixCellReads: Reads[ConfusionMatrixCell] = SnakeJson.reads[ConfusionMatrixCell]
  private implicit val confusionMatrixReads: Reads[ConfusionMatrix] = SnakeJson.reads[ConfusionMatrix]

  implicit val cvTrainTaskParamsWrites: Writes[CVTrainTaskParams] = SnakeJson.writes[CVTrainTaskParams]
  implicit val cvTrainTaskResultReads: Reads[CVTrainTaskResult] = SnakeJson.reads[CVTrainTaskResult]

  implicit val cvScoreTaskParamsWrites: Writes[CVScoreTaskParams] = SnakeJson.writes[CVScoreTaskParams]
  implicit val cvScoreTaskResultWrites: Reads[CVScoreTaskResult] = SnakeJson.reads[CVScoreTaskResult]

  implicit val cvPredictTaskParamsWrites: Writes[CVPredictTaskParams] = SnakeJson.writes[CVPredictTaskParams]
  implicit val cvPredictTaskResultWrites: Reads[CVPredictTaskResult] = SnakeJson.reads[CVPredictTaskResult]
}
