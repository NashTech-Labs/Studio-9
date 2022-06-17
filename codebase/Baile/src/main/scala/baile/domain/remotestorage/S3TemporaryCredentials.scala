package baile.domain.remotestorage

case class S3TemporaryCredentials(
  region: String,
  bucketName: String,
  accessKey: String,
  secretKey: String,
  sessionToken: String
) extends TemporaryCredentials
