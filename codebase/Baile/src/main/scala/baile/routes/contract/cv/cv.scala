package baile.routes.contract

import baile.domain.cv.model._
import baile.domain.cv.prediction.CVPredictionStatus
import baile.utils.json.{ EnumFormatBuilder, EnumWritesBuilder }
import play.api.libs.json._

package object cv {

  implicit val CVModelStatusWrites: Writes[CVModelStatus] = EnumWritesBuilder.build {
    case CVModelStatus.Saving => "SAVING"
    case CVModelStatus.Active => "ACTIVE"
    case CVModelStatus.Training => "TRAINING"
    case CVModelStatus.Pending => "PENDING"
    case CVModelStatus.Predicting => "PREDICTING"
    case CVModelStatus.Error => "ERROR"
    case CVModelStatus.Cancelled => "CANCELLED"
  }

  implicit val CVModelLocalizationModeFormat: Format[CVModelLocalizationMode] = EnumFormatBuilder.build(
    {
      case "CAPTIONS" => CVModelLocalizationMode.Captions
      case "TAGS" => CVModelLocalizationMode.Tags
    },
    {
      case CVModelLocalizationMode.Captions => "CAPTIONS"
      case CVModelLocalizationMode.Tags => "TAGS"
    },
    "CV localization mode"
  )

  implicit val PredictionStatusFormat: Format[CVPredictionStatus] = EnumFormatBuilder.build(
    {
      case "NEW" => CVPredictionStatus.New
      case "RUNNING" => CVPredictionStatus.Running
      case "ERROR" => CVPredictionStatus.Error
      case "DONE" => CVPredictionStatus.Done
    },
    {
      case CVPredictionStatus.New => "NEW"
      case CVPredictionStatus.Running => "RUNNING"
      case CVPredictionStatus.Error => "ERROR"
      case CVPredictionStatus.Done => "DONE"
    },
    "CV prediction status"
  )
}
