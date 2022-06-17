package baile.routes.contract

import baile.domain.dataset.DatasetStatus
import baile.utils.json.EnumWritesBuilder
import play.api.libs.json.Writes

package object dataset {

  implicit val DatasetStatusWrites: Writes[DatasetStatus] = EnumWritesBuilder.build {
    case DatasetStatus.Active => "IDLE"
    case DatasetStatus.Failed => "ERROR"
    case DatasetStatus.Importing => "IMPORTING"
    case DatasetStatus.Exporting => "EXPORTING"
  }

}
