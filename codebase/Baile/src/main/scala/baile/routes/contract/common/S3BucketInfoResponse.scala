package baile.routes.contract.common

import baile.daocommons.WithId
import baile.domain.common.S3Bucket
import play.api.libs.json.{ Json, OWrites }

case class S3BucketInfoResponse(
  id: String,
  name: String
)

object S3BucketInfoResponse {
  def fromDomain(bucket: WithId[S3Bucket.AccessOptions]): S3BucketInfoResponse = S3BucketInfoResponse(
    id = bucket.id,
    name = bucket.entity.bucketName
  )

  implicit val S3BucketInfoResponseWrites: OWrites[S3BucketInfoResponse] = Json.writes[S3BucketInfoResponse]
}
