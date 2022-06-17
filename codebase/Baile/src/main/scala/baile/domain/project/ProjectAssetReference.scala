package baile.domain.project

import baile.domain.asset.AssetReference

case class ProjectAssetReference(assetReference: AssetReference, folderId: Option[String])
