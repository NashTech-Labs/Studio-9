package cortex.task.video_uploading

import cortex.JsonSupport.SnakeJson
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json.{ OWrites, Reads }

object VideoUploadingParams {

  case class VideoImportTaskParams(
      inputS3Params:    S3AccessParams,
      outputS3Params:   S3AccessParams,
      videoPath:        String,
      frameCaptureRate: Int,
      albumPath:        String,
      blockSize:        Int
  ) extends TaskParams

  case class VideoImportTaskResult(
      importedFrames: Seq[Frame],
      videoFilePath:  String,
      videoFileName:  String,
      videoFrameRate: Double,
      videoHeight:    Int,
      videoWidth:     Int,
      videoSize:      Long
  ) extends TaskResult

  case class Frame(
      fileName: String,
      fileSize: Long
  )

  implicit val videoImportTaskParamsWrites: OWrites[VideoImportTaskParams] = SnakeJson.writes[VideoImportTaskParams]
  private implicit val frame: Reads[Frame] = SnakeJson.reads[Frame]
  implicit val videoImportTaskResultReads: Reads[VideoImportTaskResult] = SnakeJson.reads[VideoImportTaskResult]
}
