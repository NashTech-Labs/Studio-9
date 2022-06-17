package cortex.api.taurus

import play.api.libs.json.{ Json, OFormat }

/**
  * AWS settings for accessing S3 bucket and reading images from it.
  * @param awsSessionToken Optional in case of permanent credentials.
  * @param imagesPath Prefix of image files which should be processed. Relative to bucket name. S3 prefix in AWS terminology.
  */
case class S3ImagesSourceSettings(
    awsRegion: String,
    awsAccessKey: String,
    awsSecretKey: String,
    awsSessionToken: Option[String],
    bucketName: String,
    imagesPath: String
)

object S3ImagesSourceSettings {
  implicit val format: OFormat[S3ImagesSourceSettings] = Json.format[S3ImagesSourceSettings]
}
