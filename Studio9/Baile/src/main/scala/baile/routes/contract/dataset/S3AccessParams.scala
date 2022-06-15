package baile.routes.contract.dataset

import baile.routes.contract.common.S3BucketReference
import play.api.libs.json.{ Json, Reads }

case class S3AccessParams(
  s3Bucket: S3BucketReference,
  path: String
)

object S3AccessParams {
  implicit val S3AccessParamsReads: Reads[S3AccessParams] = Json.reads[S3AccessParams]
}
