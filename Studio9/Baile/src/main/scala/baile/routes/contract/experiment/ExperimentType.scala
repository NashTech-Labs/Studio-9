package baile.routes.contract.experiment

sealed trait ExperimentType

object ExperimentType {
  case object CVTLTrain extends ExperimentType
  case object TabularTrain extends ExperimentType
  case object GenericExperiment extends ExperimentType
}

