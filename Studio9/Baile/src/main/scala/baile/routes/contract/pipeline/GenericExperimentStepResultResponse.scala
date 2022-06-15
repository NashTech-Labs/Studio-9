package baile.routes.contract.pipeline

import baile.domain.asset.AssetReference
import baile.domain.pipeline.result.GenericExperimentStepResult
import baile.routes.contract.asset.AssetReferenceFormat
import play.api.libs.json.{ JsValue, Json, Writes }

sealed trait GenericExperimentStepResultResponse {
  val stepId: String
  val assets: Seq[AssetReference]
}

object GenericExperimentStepResultResponse {

  case class Error(
    stepId: String,
    assets: Seq[AssetReference],
    errorMessage: String
  ) extends GenericExperimentStepResultResponse

  case class Success(
    stepId: String,
    assets: Seq[AssetReference],
    summaries: Seq[PipelineOperatorApplicationSummaryResponse],
    outputValues: Map[String, PipelineResultValueResponse],
    executionTime: Long
  ) extends GenericExperimentStepResultResponse


  def fromDomain(in: GenericExperimentStepResult): GenericExperimentStepResultResponse = {
    in.failureMessage match {
      case Some(failureMessage) => Error(
        stepId = in.id,
        assets = in.assets,
        errorMessage = failureMessage
      )

      case None => Success(
        stepId = in.id,
        assets = in.assets,
        summaries = in.summaries.map(PipelineOperatorApplicationSummaryResponse.fromDomain),
        outputValues = in.outputValues map { case (k, v) =>
          (k.toString, PipelineResultValueResponse.fromDomain(v))
        },
        executionTime = in.executionTime
      )
    }
  }


  private implicit val GenericExperimentStepErrorResultResponseWrites: Writes[Error] = Json.writes[Error]
  private implicit val GenericExperimentStepSuccessResultResponseWrites: Writes[Success] = Json.writes[Success]

  implicit val GenericExperimentStepResultResponseWrite: Writes[GenericExperimentStepResultResponse] =
    new Writes[GenericExperimentStepResultResponse] {
      override def writes(result: GenericExperimentStepResultResponse): JsValue = {
        result match {
          case result: Success =>
            GenericExperimentStepSuccessResultResponseWrites.writes(result)
          case result: Error =>
            GenericExperimentStepErrorResultResponseWrites.writes(result)
        }
      }
    }

}
