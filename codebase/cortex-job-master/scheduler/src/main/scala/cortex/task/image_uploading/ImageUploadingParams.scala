package cortex.task.image_uploading

import cortex.JsonSupport.SnakeJson
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.image_uploading.ImageUploadingParams.ProcessingType
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json.{ JsValue, OWrites, Reads, Writes }

object ImageUploadingParams {

  /**
   *
   * @param inputS3Params user s3 params
   * @param outputS3Params params which is used by cortex-job-master for destination of user images
   * @param blockSize amount of images in single hdf5 block
   */
  case class S3ImportTaskParams(
      images:                  Seq[LabeledImageRequest],
      processingType:          ProcessingType,
      albumPath:               String,
      inputS3Params:           S3AccessParams,
      outputS3Params:          S3AccessParams,
      blockSize:               Int,
      applyLogTransformations: Boolean
  ) extends TaskParams

  case class S3ImportTaskResult(
      succeed: Seq[LabeledImageResult],
      failed:  Seq[FailedImage]
  ) extends TaskResult

  /**
   *
   * @param imagePath the path which was generated after image transition
   * @param defaultLabels
   * @param metaPath path to a meta file
   */
  case class LabeledImageRequest(
      imagePath:     String,
      defaultLabels: Seq[String]    = Seq(),
      metaPath:      Option[String] = None
  )

  sealed trait ProcessingType {
    val stringValue: String
  }
  object ProcessingType {
    case object SAR extends ProcessingType {
      override val stringValue: String = {
        "SAR"
      }
    }
    case object IMG extends ProcessingType {
      override val stringValue: String = {
        "IMG"
      }
    }
    //raw magnitude image
    case object RMI extends ProcessingType {
      override val stringValue: String = {
        "RMI"
      }
    }
  }

  case class LabeledImageResult(
      imagePath:    String,
      originalPath: String,
      labels:       Seq[String],
      metadata:     Map[String, String]
  )

  /**
   * @param path original path of an image
   */
  case class FailedImage(path: String, reason: String)

  private implicit object ProcessingTypeWrites extends Writes[ProcessingType] {
    override def writes(pt: ProcessingType): JsValue = SnakeJson.toJson(pt.stringValue)
  }
  implicit val labeledImageRequestWrites: OWrites[LabeledImageRequest] = SnakeJson.writes[LabeledImageRequest]
  implicit val s3ImportTaskParamsWrites: OWrites[S3ImportTaskParams] = SnakeJson.writes[S3ImportTaskParams]

  implicit val labeledImageResultReads: Reads[LabeledImageResult] = SnakeJson.reads[LabeledImageResult]
  implicit val failedImageReads: Reads[FailedImage] = SnakeJson.reads[FailedImage]
  implicit val s3ImportTaskResultReads: Reads[S3ImportTaskResult] = SnakeJson.reads[S3ImportTaskResult]
}
