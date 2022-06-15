package cortex.task.computer_vision

import cortex.JsonSupport.SnakeJson
import cortex.task.common.ClassReference
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json.{ OWrites, Reads }

object ModelImportParams {

  case class ModelImportTaskParams(
      modelPath:                      String,
      featureExtractorClassReference: Option[ClassReference],
      classReference:                 ClassReference,
      modelType:                      Option[String],
      modelsBasePath:                 String,
      outputS3Params:                 S3AccessParams
  ) extends TaskParams

  case class ModelImportTaskResult(
      modelId:            Option[String],
      featureExtractorId: Option[String]
  ) extends TaskResult

  implicit val modelImportTaskParamsWrites: OWrites[ModelImportTaskParams] = SnakeJson.writes[ModelImportTaskParams]
  implicit val modelImportTaskResultReads: Reads[ModelImportTaskResult] = SnakeJson.reads[ModelImportTaskResult]
}
