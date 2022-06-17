package cortex.task.pipeline_runner

import cortex.JsonSupport.SnakeJson
import cortex.TaskParams
import cortex.task.StorageAccessParams.S3AccessParams
import play.api.libs.json.Writes

object PipelineRunnerParams {

  case class PipelineRunnerTaskParams(
      requestPath:    String,
      s3AccessParams: S3AccessParams,
      baileUrl:       String,
      sqlServerUrl:   String
  ) extends TaskParams

  implicit val cvPredictTaskParamsWrites: Writes[PipelineRunnerTaskParams] = SnakeJson.writes[PipelineRunnerTaskParams]
}
