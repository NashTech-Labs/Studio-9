package baile.routes.contract

import baile.domain.experiment.ExperimentStatus
import baile.utils.json.{ EnumFormatBuilder, EnumWritesBuilder }
import play.api.libs.json.{ Format, Writes }

package object experiment {

  implicit val ExperimentStatusWrites: Writes[ExperimentStatus] = EnumWritesBuilder.build {
    case ExperimentStatus.Running => "RUNNING"
    case ExperimentStatus.Completed => "COMPLETED"
    case ExperimentStatus.Error => "ERROR"
    case ExperimentStatus.Cancelled => "CANCELLED"
  }

  implicit val ExperimentTypeFormat: Format[ExperimentType] = EnumFormatBuilder.build[ExperimentType](
    {
      case "CVTLTrain" => ExperimentType.CVTLTrain
      case "TabularTrain" => ExperimentType.TabularTrain
      case "GenericExperiment" => ExperimentType.GenericExperiment
    },
    {
      case ExperimentType.CVTLTrain => "CVTLTrain"
      case ExperimentType.TabularTrain => "TabularTrain"
      case ExperimentType.GenericExperiment => "GenericExperiment"
    },
    "ExperimentType"
  )

}
