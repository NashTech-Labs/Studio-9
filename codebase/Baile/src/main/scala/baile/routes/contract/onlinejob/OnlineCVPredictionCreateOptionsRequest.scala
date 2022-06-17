package baile.routes.contract.onlinejob

import baile.routes.contract.onlinejob.OnlineJobOptionsType.OnlineCvPrediction
import baile.services.onlinejob.{ OnlineJobCreateOptions, OnlinePredictionCreateOptions }
import play.api.libs.json.{ Json, Reads }

case class OnlineCVPredictionCreateOptionsRequest(
  inputBucketId: String,
  inputImagesPath: String,
  outputAlbumName: String
) extends OnlineJobCreateOptionsRequest {
  override val `type`: OnlineJobOptionsType = OnlineCvPrediction
  def toDomain(id: String): OnlineJobCreateOptions = OnlinePredictionCreateOptions(
    modelId = id,
    bucketId = inputBucketId,
    inputImagesPath = inputImagesPath,
    outputAlbumName = outputAlbumName
  )
}

object OnlineCVPredictionCreateOptionsRequest {
  implicit val OnlineCVPredictionCreateOptionsRequestReads: Reads[OnlineCVPredictionCreateOptionsRequest] =
    Json.reads[OnlineCVPredictionCreateOptionsRequest]
}
