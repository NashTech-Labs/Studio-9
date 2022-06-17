package baile.dao.images

import baile.domain.images.AlbumLabelMode
import baile.domain.images.AlbumLabelMode.{ Classification, Localization }

object AlbumLabelModeSerializers {

  def stringToAlbumLabelMode(labelModeString: String): AlbumLabelMode = {
    labelModeString match {
      case "CLASSIFICATION" => Classification
      case "LOCALIZATION" => Localization
    }
  }

  def albumLabelModeToString(labelMode: AlbumLabelMode): String = {
    labelMode match {
      case Classification => "CLASSIFICATION"
      case Localization => "LOCALIZATION"
    }
  }

}
