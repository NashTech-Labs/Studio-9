package baile.routes.contract.pipeline

import baile.domain.pipeline.result.{ ConfusionMatrix, PipelineOperatorApplicationSummary, SimpleSummary }
import play.api.libs.json.{ JsString, OWrites }

trait PipelineOperatorApplicationSummaryResponse

object PipelineOperatorApplicationSummaryResponse {

  def fromDomain(in: PipelineOperatorApplicationSummary): PipelineOperatorApplicationSummaryResponse = in match {
    case confusionMatrix: ConfusionMatrix => ConfusionMatrixResponse.fromDomain(confusionMatrix)
    case simpleSummary: SimpleSummary => SimpleSummaryResponse.fromDomain(simpleSummary)
  }

  implicit val PipelineOperatorApplicationSummaryResponseWrites: OWrites[PipelineOperatorApplicationSummaryResponse] =
    OWrites(
      (result: PipelineOperatorApplicationSummaryResponse) => {
        val (childJson, resultType) = result match {
          case result: ConfusionMatrixResponse =>
            (ConfusionMatrixResponse.ConfusionMatrixResponseWrites.writes(result), "CONFUSION_MATRIX")
          case result: SimpleSummaryResponse =>
            (SimpleSummaryResponse.SimpleSummaryResponseWrites.writes(result), "SIMPLE")
        }
        childJson + ("type" -> JsString(resultType))
      }
    )

}
