package baile.domain.onlinejob

import baile.domain.asset.{ AssetReference, AssetType }

case class OnlinePredictionOptions(
  streamId: String,
  modelId: String,
  bucketId: String,
  inputImagesPath: String,
  outputAlbumId: String
) extends OnlineJobOptions {
  override val target: AssetReference = AssetReference(modelId, AssetType.CvModel)
}
