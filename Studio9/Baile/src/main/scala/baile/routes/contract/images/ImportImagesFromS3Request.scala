package baile.routes.contract.images

import baile.routes.contract.common.S3BucketReference
import play.api.libs.functional.syntax._
import play.api.libs.json.{ JsPath, Reads }

case class ImportImagesFromS3Request(
  bucket: S3BucketReference,
  imagesPath: String,
  labelsCSVPath: Option[String],
  applyLogTransformation: Option[Boolean]
)

object ImportImagesFromS3Request {
  implicit val ImportImagesFromS3Reads: Reads[ImportImagesFromS3Request] = (
    S3BucketReference.S3BucketReads and
      (JsPath \ "S3ImagesPath").read[String] and
      (JsPath \ "S3CSVPath").readNullable[String] and
      (JsPath \ "applyLogTransformation").readNullable[Boolean]
    )((bucket, imagesPath, labelsCSVPath, applyLogTransformation) =>
    ImportImagesFromS3Request(bucket, imagesPath, labelsCSVPath, applyLogTransformation)
  )
}
