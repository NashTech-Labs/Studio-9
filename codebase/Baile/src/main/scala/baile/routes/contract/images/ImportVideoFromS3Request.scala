package baile.routes.contract.images

import baile.routes.contract.common.S3BucketReference
import play.api.libs.json.{ Reads, JsPath }
import play.api.libs.functional.syntax._

case class ImportVideoFromS3Request(
  bucket: S3BucketReference,
  videoPath: String,
  frameRateDivider: Option[Int]
)


object ImportVideoFromS3Request {
  implicit val ImportVideoFromS3Reads: Reads[ImportVideoFromS3Request] = (
    S3BucketReference.S3BucketReads and
      (JsPath \ "S3VideoPath").read[String] and
      (JsPath \ "frameRateDivider").readNullable[Int]
    )((bucket, csvPath, frameRateDivider) => ImportVideoFromS3Request(bucket, csvPath, frameRateDivider))
}
