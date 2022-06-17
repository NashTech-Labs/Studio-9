package cortex.task.computer_vision

import cortex.JsonSupport._
import cortex.task.common.ClassReference
import cortex.task.StorageAccessParams._
import cortex.task.computer_vision.AutoAugmentation.{ AutoAugmentationParams, AutoAugmentationResult }
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json._

object LocalizationParams {

  case class Tag(xMin: Int, yMin: Int, xMax: Int, yMax: Int, label: String, confidence: Option[Double] = None)

  case class PredictionResult(filename: String, tags: List[Tag])

  case class ConfusionMatrixCell(actualLabelIndex: Option[Int], predictedLabelIndex: Option[Int], value: Int)

  case class ConfusionMatrix(confusionMatrixCells: Seq[ConfusionMatrixCell], labels: Seq[String])

  case class InputSize(width: Int, height: Int)

  case class FeatureExtractorSettings(
      tuneFeatureExtractor:         Boolean,
      featureExtractorLearningRate: Option[Double],
      featureExtractorParameters:   Map[String, ParameterValue] = Map.empty,
      featureExtractorId:           Option[String]              = None
  )

  /** TRAIN **/

  case class TrainTaskParams(
      albumPath:                      String,
      imagePaths:                     Seq[String],
      tags:                           Seq[Seq[Tag]],
      displayNames:                   Option[Seq[Option[String]]],
      referenceIds:                   Seq[Option[String]],
      modelsBasePath:                 String,
      featureExtractorClassReference: ClassReference,
      classReference:                 ClassReference,
      outputS3Params:                 S3AccessParams,
      augmentationParams:             Option[AutoAugmentationParams],
      outputTableS3Path:              Option[String],
      inputSize:                      Option[InputSize],
      labelsOfInterest:               Seq[String],
      thresholds:                     Seq[Double],
      defaultVisualThreshold:         Option[Double],
      iouThreshold:                   Option[Double],
      modelLearningRate:              Option[Double],
      featureExtractorSettings:       FeatureExtractorSettings,
      modelParameters:                Map[String, ParameterValue]    = Map.empty,
      action:                         String                         = "train",
      testMode:                       Boolean                        = false
  ) extends TaskParams {
    assert(tags.length == imagePaths.length, "Lengths don't match")
    assert(referenceIds.length == imagePaths.length, "Lengths don't match")
    assert(displayNames.forall(_.length == imagePaths.length), "Lengths don't match")
  }

  case class TrainTaskResult(
      modelId:                  String,
      featureExtractorId:       String,
      predictions:              Seq[PredictionResult],
      mAP:                      Double,
      confusionMatrix:          ConfusionMatrix,
      augmentationResult:       Option[AutoAugmentationResult],
      dataFetchTime:            Long,
      trainingTime:             Long,
      saveModelTime:            Long,
      saveFeatureExtractorTime: Long,
      predictionTime:           Long,
      predictionTable:          Option[PredictionTable]
  ) extends TaskResult

  case class ScoreTaskParams(
      albumPath:                      String,
      imagePaths:                     Seq[String],
      tags:                           Seq[Seq[Tag]],
      modelId:                        String,
      featureExtractorClassReference: ClassReference,
      classReference:                 ClassReference,
      displayNames:                   Option[Seq[Option[String]]],
      referenceIds:                   Seq[Option[String]],
      modelsBasePath:                 String,
      outputS3Params:                 S3AccessParams,
      outputTableS3Path:              Option[String],
      labelsOfInterest:               Seq[String],
      thresholds:                     Seq[Double],
      defaultVisualThreshold:         Option[Double],
      iouThreshold:                   Option[Double],
      action:                         String                      = "score"
  ) extends TaskParams {
    assert(tags.length == imagePaths.length, "Lengths don't match")
    assert(outputTableS3Path.isEmpty || referenceIds.length == imagePaths.length, "Lengths don't match")
    assert(displayNames.forall(_.length == imagePaths.length), "Lengths don't match")
  }

  case class ScoreTaskResult(
      predictions:     Seq[PredictionResult],
      mAP:             Double,
      confusionMatrix: ConfusionMatrix,
      dataFetchTime:   Long,
      loadModelTime:   Long,
      scoreTime:       Long,
      predictionTable: Option[PredictionTable]
  ) extends TaskResult

  case class PredictTaskParams(
      albumPath:                      String,
      imagePaths:                     Seq[String],
      modelId:                        String,
      featureExtractorClassReference: ClassReference,
      classReference:                 ClassReference,
      displayNames:                   Option[Seq[Option[String]]],
      referenceIds:                   Seq[Option[String]],
      modelsBasePath:                 String,
      outputS3Params:                 S3AccessParams,
      outputTableS3Path:              Option[String],
      labelsOfInterest:               Seq[String],
      thresholds:                     Seq[Double],
      defaultVisualThreshold:         Option[Double],
      action:                         String                      = "predict"
  ) extends TaskParams {
    assert(outputTableS3Path.isEmpty || referenceIds.length == imagePaths.length, "Lengths don't match")
    assert(displayNames.forall(_.length == imagePaths.length), "Lengths don't match")
  }

  case class PredictTaskResult(
      predictions:     Seq[PredictionResult],
      dataFetchTime:   Long,
      loadModelTime:   Long,
      predictionTime:  Long,
      predictionTable: Option[PredictionTable]
  ) extends TaskResult

  case class ComposeVideoTaskParams(
      albumPath:              String,
      imagePaths:             Seq[String],
      tags:                   Seq[Seq[Tag]],
      outputS3Params:         S3AccessParams,
      videoFilePath:          String,
      videoAssembleFrameRate: Double,
      videoAssembleHeight:    Int,
      videoAssembleWidth:     Int,
      labelsOfInterest:       Seq[String],
      action:                 String         = "compose-video"
  ) extends TaskParams {
    assert(tags.length == imagePaths.length, "Lengths don't match")
  }

  case class ComposeVideoTaskResult(
      videoFileSize: Long
  ) extends TaskResult

  implicit object TagReads extends Reads[Tag] {
    override def reads(json: JsValue): JsResult[Tag] = json match {
      case JsArray(Seq(JsNumber(xMin), JsNumber(yMin), JsNumber(xMax), JsNumber(yMax), JsString(label), JsNumber(confidence))) =>
        JsSuccess(Tag(
          xMin       = xMin.toInt,
          yMin       = yMin.toInt,
          xMax       = xMax.toInt,
          yMax       = yMax.toInt,
          label      = label,
          confidence = Some(confidence.toDouble)
        ))
      case _ => JsError(s"Invalid Tag: $json")
    }
  }
  implicit object TagWrites extends Writes[Tag] {
    override def writes(t: Tag): JsValue = JsArray(Seq(
      JsNumber(t.xMin),
      JsNumber(t.yMin),
      JsNumber(t.xMax),
      JsNumber(t.yMax),
      JsString(t.label)
    ))
  }

  implicit val predictionResultReads: Reads[PredictionResult] = SnakeJson.reads[PredictionResult]
  implicit val confusionMatrixCellReads: Reads[ConfusionMatrixCell] = SnakeJson.reads[ConfusionMatrixCell]
  implicit val confusionMatrixReads: Reads[ConfusionMatrix] = SnakeJson.reads[ConfusionMatrix]

  private implicit val inputSizeWrites: Writes[InputSize] = SnakeJson.writes[InputSize]
  private implicit val featureExtractorSettingsWrites: Writes[FeatureExtractorSettings] =
    SnakeJson.writes[FeatureExtractorSettings]
  implicit val trainTaskParamsWrites: Writes[TrainTaskParams] = SnakeJson.writes[TrainTaskParams]
  implicit val trainTaskResultReads: Reads[TrainTaskResult] = SnakeJson.reads[TrainTaskResult]

  implicit val scoreTaskParamsWrites: Writes[ScoreTaskParams] = SnakeJson.writes[ScoreTaskParams]
  implicit val scoreTaskResultReads: Reads[ScoreTaskResult] = SnakeJson.reads[ScoreTaskResult]

  implicit val predictTaskParamsWrites: Writes[PredictTaskParams] = SnakeJson.writes[PredictTaskParams]
  implicit val predictTaskResultReads: Reads[PredictTaskResult] = SnakeJson.reads[PredictTaskResult]

  implicit val composeVideoTaskParamsWrites: Writes[ComposeVideoTaskParams] = SnakeJson.writes[ComposeVideoTaskParams]
  implicit val composeVideoTaskResultReads: Reads[ComposeVideoTaskResult] = SnakeJson.reads[ComposeVideoTaskResult]
}
