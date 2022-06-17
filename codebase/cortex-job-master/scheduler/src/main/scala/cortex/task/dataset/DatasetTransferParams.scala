package cortex.task.dataset

import cortex.JsonSupport._
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json._

object DatasetTransferParams {

  case class DatasetTransferTaskParams(
      filePaths:          Seq[String],
      sourcePath:         String,
      targetPrefix:       String,
      inputAccessParams:  S3AccessParams,
      outputAccessParams: S3AccessParams
  ) extends TaskParams

  case class DatasetTransferTaskResult(
      succeed: Seq[UploadedFileResult],
      failed:  Seq[FailedFile]
  ) extends TaskResult

  case class UploadedFileResult(
      path: String
  )

  case class FailedFile(
      path:   String,
      reason: String
  )

  private implicit val uploadedFileResultReads: Reads[UploadedFileResult] = SnakeJson.reads[UploadedFileResult]
  private implicit val failedFileReads: Reads[FailedFile] = SnakeJson.reads[FailedFile]

  implicit val datasetTransferTaskParamsWrites: Writes[DatasetTransferTaskParams] = SnakeJson.writes[DatasetTransferTaskParams]
  implicit val datasetTransferTaskResultReads: Reads[DatasetTransferTaskResult] = SnakeJson.reads[DatasetTransferTaskResult]
}
