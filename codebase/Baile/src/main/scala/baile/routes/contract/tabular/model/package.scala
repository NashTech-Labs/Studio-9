package baile.routes.contract.tabular

import baile.domain.tabular.model.TabularModelStatus
import baile.utils.json.EnumWritesBuilder
import play.api.libs.json._

package object model {

  implicit val TabularModelStatusWrites: Writes[TabularModelStatus] = EnumWritesBuilder.build {
    case TabularModelStatus.Active => "ACTIVE"
    case TabularModelStatus.Training => "TRAINING"
    case TabularModelStatus.Predicting => "PREDICTING"
    case TabularModelStatus.Error => "ERROR"
    case TabularModelStatus.Cancelled => "CANCELLED"
    case TabularModelStatus.Saving => "SAVING"
  }

}
