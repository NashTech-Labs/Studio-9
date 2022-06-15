package baile.routes.contract.cv

import baile.domain.cv.pipeline.{ CVTLTrainPipeline => DomainCVTLTrainPipeline }
import baile.routes.contract.experiment.ExperimentPipeline
import play.api.libs.json.{ Json, OFormat }

case class CVTLTrainPipeline(
  step1: CVTLTrainStep1Params,
  step2: Option[CVTLTrainStep2Params]
) extends ExperimentPipeline {

  def toDomain: DomainCVTLTrainPipeline = DomainCVTLTrainPipeline(
    step1.toDomain,
    step2.map(_.toDomain)
  )

}

object CVTLTrainPipeline {

  def fromDomain(in: DomainCVTLTrainPipeline): CVTLTrainPipeline = in match {
    case DomainCVTLTrainPipeline(stepOne, stepTwo) => CVTLTrainPipeline(
      CVTLTrainStep1Params.fromDomain(stepOne),
      stepTwo.map(CVTLTrainStep2Params.fromDomain)
    )
  }

  implicit val CVTLTrainPipelineFormat: OFormat[CVTLTrainPipeline] =
    Json.format[CVTLTrainPipeline]

}
