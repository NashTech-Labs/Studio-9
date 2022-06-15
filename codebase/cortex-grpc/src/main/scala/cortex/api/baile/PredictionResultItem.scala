package cortex.api.baile

import play.api.libs.json.{ Json, OFormat }

/**
  * Single item of the online prediction result, related to a single image.
  *
  * @param filePath Path to the file. Relative to the system (not user!) bucket name and target prefix.
  * @param fileSize Size of the file in bytes.
  * @param fileName Original name of the file which was defined by user.
  * @param metadata Map which contains arbitrary metadata of this image.
  * @param label Label, associated with this image.
  * @param confidence Shows how ''confident'' model is about the label for the image.
  *                   It is a double in range from 0 to 1, with 1 being '''completely confident'''.
  * @see [[cortex.api.job.album.uploading.S3ImagesImportRequest]]
  */
case class PredictionResultItem(
    filePath:   String,
    fileSize:   Long,
    fileName:   String,
    metadata:   Map[String, String],
    label:      String,
    confidence: Double
) {
  require(confidence >= 0.0 && confidence <= 1.0)
}

object PredictionResultItem {
  implicit val format: OFormat[PredictionResultItem] = Json.format[PredictionResultItem]
}
