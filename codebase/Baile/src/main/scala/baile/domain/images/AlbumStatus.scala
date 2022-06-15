package baile.domain.images

import baile.domain.asset.AssetStatus

sealed trait AlbumStatus extends AssetStatus

object AlbumStatus {

  case object Saving extends AlbumStatus

  case object Uploading extends AlbumStatus

  case object Active extends AlbumStatus

  case object Failed extends AlbumStatus

}
