package baile.domain.asset

sealed trait AssetType

object AssetType {

  case object TabularModel extends AssetType

  case object TabularPrediction extends AssetType

  case object Table extends AssetType

  case object Flow extends AssetType

  case object Album extends AssetType

  case object CvModel extends AssetType

  case object CvPrediction extends AssetType

  case object OnlineJob extends AssetType

  case object DCProject extends AssetType

  case object Experiment extends AssetType

  case object Pipeline extends AssetType

  case object Dataset extends AssetType

}
