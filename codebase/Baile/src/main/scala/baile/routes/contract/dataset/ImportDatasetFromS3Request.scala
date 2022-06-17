package baile.routes.contract.dataset

import play.api.libs.json.{ Json, Reads }

case class ImportDatasetFromS3Request(
  from: S3AccessParams
)

object ImportDatasetFromS3Request {
  implicit val ImportDatasetFromS3Reads: Reads[ImportDatasetFromS3Request] =
    Json.reads[ImportDatasetFromS3Request]
}
