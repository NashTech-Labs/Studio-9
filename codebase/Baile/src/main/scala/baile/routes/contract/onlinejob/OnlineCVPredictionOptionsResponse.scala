package baile.routes.contract.onlinejob

import baile.domain.onlinejob.OnlinePredictionOptions
import baile.routes.contract.onlinejob.OnlineJobOptionsType.OnlineCvPrediction
import play.api.libs.json._

case class OnlineCVPredictionOptionsResponse(
  inputBucketId: String,
  inputImagesPath: String,
  outputAlbumId: String
) extends OnlineJobOptionsResponse {
  override val `type`: OnlineJobOptionsType = OnlineCvPrediction
}

object OnlineCVPredictionOptionsResponse {

  def fromDomain(
    onlinePredictionOptions: OnlinePredictionOptions
  ): OnlineCVPredictionOptionsResponse = this(
    onlinePredictionOptions.bucketId,
    onlinePredictionOptions.inputImagesPath,
    onlinePredictionOptions.outputAlbumId
  )

  implicit val OnlineCVPredictionOptionsResponseWrites: OWrites[OnlineCVPredictionOptionsResponse] =
    Json.writes[OnlineCVPredictionOptionsResponse]

}
