package baile.services.onlinejob

import baile.domain.asset.{ AssetReference, AssetType }

case class OnlinePredictionCreateOptions(
  modelId: String,
  bucketId: String,
  inputImagesPath: String,
  outputAlbumName: String
) extends OnlineJobCreateOptions {
  override val target: AssetReference = AssetReference(modelId, AssetType.CvModel)
}
