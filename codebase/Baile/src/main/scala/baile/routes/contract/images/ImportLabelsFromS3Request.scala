package baile.routes.contract.images

import baile.routes.contract.common.S3BucketReference
import play.api.libs.json.{ Reads, JsPath }
import play.api.libs.functional.syntax._

case class ImportLabelsFromS3Request (
  bucket: S3BucketReference,
  csvPath: String
)

object ImportLabelsFromS3Request {
  implicit val ImportLabelsFromS3Reads: Reads[ImportLabelsFromS3Request] = (
    S3BucketReference.S3BucketReads and
    (JsPath \ "S3CSVPath").read[String]
  )((bucket, csvPath) => ImportLabelsFromS3Request(bucket, csvPath))
}
