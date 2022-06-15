package baile.routes.contract.dataset

import play.api.libs.json.{ Json, Reads }

case class ExportDatasetToS3Request(
  to: S3AccessParams
)

object ExportDatasetToS3Request {
  implicit val ExportDatasetToS3Reads: Reads[ExportDatasetToS3Request] =
    Json.reads[ExportDatasetToS3Request]
}
