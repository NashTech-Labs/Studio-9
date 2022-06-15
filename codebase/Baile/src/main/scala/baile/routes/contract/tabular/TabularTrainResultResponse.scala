package baile.routes.contract.tabular

import baile.domain.tabular.model.summary.{ PredictorSummary, TabularModelEvaluationSummary, TabularModelTrainSummary }
import baile.domain.tabular.result.TabularTrainResult
import baile.routes.contract.experiment.ExperimentResultResponse
import baile.routes.contract.tabular.model.summary.TabularModelSummaryResponse
import play.api.libs.json.{ Json, OWrites }

case class TabularTrainResultResponse(
  modelId: String,
  output: String,
  holdOutOutput: Option[String],
  outOfTimeOutput: Option[String],
  predictedColumn: String,
  probabilityColumns: Option[Seq[String]],
  summary: Option[TabularModelSummaryResponse],
  holdOutSummary: Option[TabularModelSummaryResponse],
  outOfTimeSummary: Option[TabularModelSummaryResponse]
) extends ExperimentResultResponse

object TabularTrainResultResponse {

  implicit val TabularTrainResultResponseWrites: OWrites[TabularTrainResultResponse] =
    Json.writes[TabularTrainResultResponse]

  def fromDomain(tabularTrainResult: TabularTrainResult): TabularTrainResultResponse = {
    def buildSummary[T](
      summary: Option[T],
      baseBuilder: (T, Seq[PredictorSummary]) => TabularModelSummaryResponse
    ): Option[TabularModelSummaryResponse] =
      summary.map(baseBuilder(_, tabularTrainResult.predictorsSummary))

    TabularTrainResultResponse(
      modelId = tabularTrainResult.modelId,
      output = tabularTrainResult.outputTableId,
      holdOutOutput = tabularTrainResult.holdOutOutputTableId,
      outOfTimeOutput = tabularTrainResult.outOfTimeOutputTableId,
      predictedColumn = tabularTrainResult.predictedColumnName,
      probabilityColumns = tabularTrainResult.classes.map(_.map(_.probabilityColumnName)),
      summary = buildSummary[TabularModelTrainSummary](
        tabularTrainResult.summary,
        TabularModelSummaryResponse.fromDomain
      ),
      holdOutSummary = buildSummary[TabularModelEvaluationSummary](
        tabularTrainResult.holdOutSummary,
        TabularModelSummaryResponse.fromDomain
      ),
      outOfTimeSummary = buildSummary[TabularModelEvaluationSummary](
        tabularTrainResult.outOfTimeSummary,
        TabularModelSummaryResponse.fromDomain
      )
    )
  }

}
