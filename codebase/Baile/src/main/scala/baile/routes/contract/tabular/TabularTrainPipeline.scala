package baile.routes.contract.tabular

import baile.domain.tabular.pipeline.{ TabularTrainPipeline => DomainTabularTrainPipeline }
import baile.routes.contract.experiment.ExperimentPipeline
import baile.routes.contract.tabular.model.ModelColumn
import play.api.libs.json.{ Json, OFormat }

case class TabularTrainPipeline(
  input: String,
  holdOutInput: Option[String],
  outOfTimeInput: Option[String],
  responseColumn: ModelColumn,
  predictorColumns: List[ModelColumn],
  samplingWeightColumn: Option[String]
) extends ExperimentPipeline {

  def toDomain: DomainTabularTrainPipeline =
    DomainTabularTrainPipeline(
      inputTableId = input,
      holdOutInputTableId = holdOutInput,
      outOfTimeInputTableId = outOfTimeInput,
      responseColumn = responseColumn.toDomain,
      predictorColumns = predictorColumns.map(_.toDomain),
      samplingWeightColumnName = samplingWeightColumn
    )

}

object TabularTrainPipeline {

  implicit val TabularTrainPipelineFormat: OFormat[TabularTrainPipeline] =
    Json.format[TabularTrainPipeline]

  def fromDomain(tabularTrainPipeline: DomainTabularTrainPipeline): TabularTrainPipeline = TabularTrainPipeline(
    holdOutInput = tabularTrainPipeline.holdOutInputTableId,
    input = tabularTrainPipeline.inputTableId,
    outOfTimeInput = tabularTrainPipeline.outOfTimeInputTableId,
    responseColumn = ModelColumn.fromDomain(tabularTrainPipeline.responseColumn),
    predictorColumns = tabularTrainPipeline.predictorColumns.map(ModelColumn.fromDomain),
    samplingWeightColumn = tabularTrainPipeline.samplingWeightColumnName
  )

}
