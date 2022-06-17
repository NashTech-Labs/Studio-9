package cortex.api.taurus

import play.api.libs.json.{ OFormat, Json }

/**
  * Stream settings required for running cortex online CV prediction job.
  *
  * @param s3Settings Source bucket S3 settings.
  * @param modelId Id of model to use for prediction.
  * @param albumId Id of album which will contain result pictures.
  * @param owner User login or system login of the one who created the stream.
  * @param targetPrefix Prefix which should be used to store all processed image files. Effectively, this is location ''where to put result files''.
  */
case class StreamSettings(
    id: String,
    s3Settings: S3ImagesSourceSettings,
    modelId: String,
    albumId: String,
    owner: String,
    targetPrefix: String
)

object StreamSettings {
  implicit val format: OFormat[StreamSettings] = Json.format[StreamSettings]
}
