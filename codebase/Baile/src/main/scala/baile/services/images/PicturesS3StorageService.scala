package baile.services.images

import akka.stream.Materializer
import baile.domain.common.S3Bucket
import baile.services.common.S3BucketService
import baile.services.remotestorage.S3StorageService

/**
 * TODO: this class should be removed once Cortex adds separate method for albums merging,
 * so that Album Service can get agnostic to storage type
 */
class PicturesS3StorageService(
  val accessOptions: S3Bucket.AccessOptions,
  s3BucketService: S3BucketService,
  s3AccessPolicyFilePath: String,
  s3CredentialsDuration: Int,
  s3CredentialsRoleArn: String,
  s3ArnPartititon: String
)(implicit materializer: Materializer) extends S3StorageService(
  accessOptions,
  s3BucketService,
  s3AccessPolicyFilePath,
  s3CredentialsDuration,
  s3CredentialsRoleArn,
  s3ArnPartititon
)
