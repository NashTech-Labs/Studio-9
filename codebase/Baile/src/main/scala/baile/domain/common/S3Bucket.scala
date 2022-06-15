package baile.domain.common


sealed trait S3Bucket

object S3Bucket {

  case class IdReference(bucketId: String) extends S3Bucket

  case class AccessOptions(
    region: String,
    bucketName: String,
    accessKey: Option[String],
    secretKey: Option[String],
    sessionToken: Option[String]
  ) extends S3Bucket
}
