package baile.routes.contract

import baile.domain.tabular.model.TabularModelStatus
import baile.domain.tabular.prediction.TabularPredictionStatus
import baile.utils.json.EnumWritesBuilder
import play.api.libs.json._

package object tabular {

  implicit val TabularModelStatusWrites: Writes[TabularModelStatus] = EnumWritesBuilder.build {
    case TabularModelStatus.Active => "ACTIVE"
    case TabularModelStatus.Training => "TRAINING"
    case TabularModelStatus.Predicting => "PREDICTING"
    case TabularModelStatus.Error => "ERROR"
    case TabularModelStatus.Cancelled => "CANCELLED"
    case TabularModelStatus.Saving => "SAVING"
  }

  implicit val TabularPredictionStatusWrites: Writes[TabularPredictionStatus] = EnumWritesBuilder.build {
    case TabularPredictionStatus.New => "NEW"
    case TabularPredictionStatus.Running => "RUNNING"
    case TabularPredictionStatus.Error => "ERROR"
    case TabularPredictionStatus.Done => "DONE"
  }

}
