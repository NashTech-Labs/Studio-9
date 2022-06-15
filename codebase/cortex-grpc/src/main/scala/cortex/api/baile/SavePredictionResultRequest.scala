package cortex.api.baile

import play.api.libs.json.{ Json, OFormat }

/**
  * @param albumId Id of album for which to save this result.
  */
case class SavePredictionResultRequest(albumId: String, results: Seq[PredictionResultItem])

object SavePredictionResultRequest {
  implicit val format: OFormat[SavePredictionResultRequest] = Json.format[SavePredictionResultRequest]
}
