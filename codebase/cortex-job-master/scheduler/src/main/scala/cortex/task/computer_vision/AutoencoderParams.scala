package cortex.task.computer_vision

import cortex.JsonSupport.SnakeJson
import cortex.task.common.ClassReference
import cortex.{ TaskParams, TaskResult }
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.computer_vision.AutoAugmentation.{ AutoAugmentationParams, AutoAugmentationResult }
import play.api.libs.json.{ OWrites, Reads }

object AutoencoderParams {

  /** Reconstructive model **/
  case class AutoencoderTrainTaskParams(
      albumPath:                      String,
      imagePaths:                     Seq[String],
      referenceIds:                   Seq[Option[String]],
      featureExtractorClassReference: ClassReference,
      classReference:                 ClassReference,
      modelsBasePath:                 String,
      outputS3Params:                 S3AccessParams,
      augmentationParams:             Option[AutoAugmentationParams],
      featureExtractorParameters:     Map[String, ParameterValue]    = Map.empty,
      modelParameters:                Map[String, ParameterValue]    = Map.empty,
      action:                         String                         = "train",
      testMode:                       Boolean                        = false
  ) extends TaskParams {
    assert(referenceIds.length == imagePaths.length, "Lengths don't match")
  }

  case class AutoencoderTrainTaskResult(
      modelId:                  String,
      featureExtractorId:       String,
      reconstructionLoss:       Double,
      augmentationResult:       Option[AutoAugmentationResult],
      dataFetchTime:            Long,
      trainingTime:             Long,
      saveModelTime:            Long,
      saveFeatureExtractorTime: Long,
      predictionTime:           Long
  ) extends TaskResult

  case class PredictionResult(filename: String, referenceId: String, imageSize: Long)

  case class AutoencoderPredictTaskParams(
      modelId:                        String,
      albumPath:                      String,
      imagePaths:                     Seq[String],
      referenceIds:                   Seq[Option[String]],
      featureExtractorClassReference: ClassReference,
      classReference:                 ClassReference,
      modelsBasePath:                 String,
      outputS3Params:                 S3AccessParams,
      outputAlbumPath:                String,
      action:                         String              = "predict"
  ) extends TaskParams {
    assert(referenceIds.length == imagePaths.length, "Lengths don't match")
  }

  case class AutoencoderPredictTaskResult(
      predictions:    Seq[PredictionResult],
      dataFetchTime:  Long,
      loadModelTime:  Long,
      predictionTime: Long
  ) extends TaskResult

  case class AutoencoderScoreTaskParams(
      modelId:                        String,
      albumPath:                      String,
      imagePaths:                     Seq[String],
      featureExtractorClassReference: ClassReference,
      classReference:                 ClassReference,
      modelsBasePath:                 String,
      outputS3Params:                 S3AccessParams,
      action:                         String         = "score"
  ) extends TaskParams {
  }

  case class AutoencoderScoreTaskResult(
      reconstructionLoss: Double,
      dataFetchTime:      Long,
      loadModelTime:      Long,
      scoreTime:          Long
  ) extends TaskResult

  implicit val autoencoderTrainTaskParamsWrites: OWrites[AutoencoderTrainTaskParams] = SnakeJson.writes[AutoencoderTrainTaskParams]
  implicit val autoencoderTrainTaskResultReads: Reads[AutoencoderTrainTaskResult] = SnakeJson.reads[AutoencoderTrainTaskResult]

  private implicit val predictionResultReads: Reads[PredictionResult] = SnakeJson.reads[PredictionResult]
  implicit val autoencoderPredictTaskParamsWrites: OWrites[AutoencoderPredictTaskParams] = SnakeJson.writes[AutoencoderPredictTaskParams]
  implicit val autoencoderPredictTaskResultReads: Reads[AutoencoderPredictTaskResult] = SnakeJson.reads[AutoencoderPredictTaskResult]

  implicit val autoencoderScoreTaskParamsWrites: OWrites[AutoencoderScoreTaskParams] = SnakeJson.writes[AutoencoderScoreTaskParams]
  implicit val autoencoderScoreTaskResultReads: Reads[AutoencoderScoreTaskResult] = SnakeJson.reads[AutoencoderScoreTaskResult]
}
