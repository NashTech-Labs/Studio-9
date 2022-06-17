package baile.routes.contract

import baile.domain.asset.{ AssetReference, AssetScope, AssetType }
import baile.domain.asset.AssetType.{ CvPrediction, _ }
import baile.utils.json.{ EnumFormatBuilder, EnumReadsBuilder }
import play.api.libs.json._

package object asset {

  implicit val AssetTypeFormat: Format[AssetType] = EnumFormatBuilder.build(
    {
      case "MODEL" => TabularModel
      case "TABLE" => Table
      case "FLOW" => Flow
      case "ALBUM" => Album
      case "CV_MODEL" => CvModel
      case "ONLINE_JOB" => OnlineJob
      case "CV_PREDICTION" => CvPrediction
      case "PREDICTION" => TabularPrediction
      case "DC_PROJECT" => DCProject
      case "EXPERIMENT" => Experiment
      case "PIPELINE" => Pipeline
      case "DATASET" => Dataset
    },
    {
      case TabularModel => "MODEL"
      case Table => "TABLE"
      case Flow => "FLOW"
      case Album => "ALBUM"
      case CvModel => "CV_MODEL"
      case OnlineJob => "ONLINE_JOB"
      case CvPrediction => "CV_PREDICTION"
      case TabularPrediction => "PREDICTION"
      case DCProject => "DC_PROJECT"
      case Experiment => "EXPERIMENT"
      case Pipeline => "PIPELINE"
      case Dataset => "DATASET"
    },
    "asset type"
  )

  implicit val AssetReferenceFormat: OFormat[AssetReference] = Json.format[AssetReference]

  implicit val AssetScopeReads: Reads[AssetScope] = EnumReadsBuilder.build(
    {
      case "all" => AssetScope.All
      case "personal" => AssetScope.Personal
      case "shared" => AssetScope.Shared
    },
    "asset scope"
  )
}
