package baile.routes.contract

import baile.domain.images._
import baile.utils.json.{ EnumFormatBuilder, EnumWritesBuilder }
import play.api.libs.json._

package object images {

  implicit val AlbumStatusWrites: Writes[AlbumStatus] = EnumWritesBuilder.build {
    case AlbumStatus.Active => "ACTIVE"
    case AlbumStatus.Failed => "ERROR"
    case AlbumStatus.Saving => "SAVING"
    case AlbumStatus.Uploading => "UPLOADING"
  }

  implicit val AlbumLabelModeFormat: Format[AlbumLabelMode] = EnumFormatBuilder.build(
    {
      case "CLASSIFICATION" => AlbumLabelMode.Classification
      case "LOCALIZATION" => AlbumLabelMode.Localization
    },
    {
      case AlbumLabelMode.Classification => "CLASSIFICATION"
      case AlbumLabelMode.Localization => "LOCALIZATION"
    },
    "album label mode"
  )

  implicit val AlbumTypeFormat: Format[AlbumType] = EnumFormatBuilder.build(
    {
      case "SOURCE" => AlbumType.Source
      case "DERIVED" => AlbumType.Derived
      case "TRAINRESULTS" => AlbumType.TrainResults
    },
    {
      case AlbumType.Source => "SOURCE"
      case AlbumType.Derived => "DERIVED"
      case AlbumType.TrainResults => "TRAINRESULTS"
    },
    "album type"
  )

  implicit val PictureTagAreaFormat: OFormat[PictureTagArea] = Json.format[PictureTagArea]
  implicit val PictureTagFormat: OFormat[PictureTag] = Json.format[PictureTag]

}
