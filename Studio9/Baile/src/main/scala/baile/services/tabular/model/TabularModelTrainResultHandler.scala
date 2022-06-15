package baile.services.tabular.model

import java.util.UUID

import baile.dao.experiment.ExperimentDao
import baile.dao.tabular.model.TabularModelDao
import baile.daocommons.WithId
import baile.domain.common.CortexModelReference
import baile.domain.experiment.{ Experiment, ExperimentStatus }
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.domain.table._
import baile.domain.tabular.model.{ TabularModel, TabularModelClass, TabularModelStatus }
import baile.domain.tabular.pipeline.TabularTrainPipeline
import baile.domain.tabular.result.TabularTrainResult
import baile.services.common.EntityUpdateFailedException
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.experiment.ExperimentCommonService
import baile.services.process.JobResultHandler
import baile.services.table.TableService
import baile.services.tabular.model.TabularModelTrainResultHandler.Meta
import baile.services.tabular.model.TabularTrainPipelineHandler.{
  EvaluationParams,
  EvaluationType,
  NonSuccessfulTerminalStatus,
  TypedEvaluationParams
}
import baile.utils.TryExtensions._
import cortex.api.job.table.ProbabilityClassColumn
import cortex.api.job.tabular.TrainResult
import play.api.libs.json.{ Json, OFormat, Reads }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class TabularModelTrainResultHandler(
  val tabularModelCommonService: TabularModelCommonService,
  val experimentCommonService: ExperimentCommonService,
  val tabularTrainPipelineHandler: TabularTrainPipelineHandler,
  val experimentDao: ExperimentDao,
  val tableService: TableService,
  val cortexJobService: CortexJobService,
  val jobMetaService: JobMetaService,
  val modelDao: TabularModelDao
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] =
    TabularModelTrainResultHandler.TabularModelTrainResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] =
    for {
      model <- tabularModelCommonService.loadModelMandatory(meta.modelId)
      _ <- tabularModelCommonService.assertModelStatus(model, TabularModelStatus.Training).toFuture
      _ <- lastStatus match {
        case CortexJobStatus.Completed =>
          for {
            experiment <- experimentCommonService.loadExperimentMandatory(meta.experimentId)
            outputPath <- cortexJobService.getJobOutputPath(jobId)
            rawJobResult <- jobMetaService.readRawMeta(jobId, outputPath)
            trainResult <- Try(TrainResult.parseFrom(rawJobResult)).toFuture
            updatedModel <- modelDao.update(
              model.id,
              _.copy(
                cortexModelReference = Some(CortexModelReference(
                  cortexId = trainResult.modelId,
                  cortexFilePath = trainResult.modelFilePath
                )),
                classNames = if (trainResult.probabilityColumns.isEmpty) {
                  None
                } else {
                  Some(trainResult.probabilityColumns.map(_.className))
                }
              )
            ).map(_.getOrElse(throw EntityUpdateFailedException(model.id, model)))
            experimentResult = TabularTrainResult(
              modelId = updatedModel.id,
              outputTableId = meta.outputTableId,
              holdOutOutputTableId = meta.holdOutEvaluationParams.map(_.outputTableId),
              outOfTimeOutputTableId = meta.outOfTimeEvaluationParams.map(_.outputTableId),
              predictedColumnName = meta.predictionResultColumnName,
              classes = if (trainResult.probabilityColumns.isEmpty) {
                None
              } else {
                Some(trainResult.probabilityColumns.map(buildModelClass))
              },
              summary = Some(tabularModelCommonService.buildTabularModelTrainSummary(trainResult.summary)),
              predictorsSummary = trainResult.predictorsSummary.map(tabularModelCommonService.buildPredictorSummary),
              holdOutSummary = None,
              outOfTimeSummary = None
            )
            _ <- experimentDao.update(experiment.id, _.copy(result = Some(experimentResult)))
            inputTable <- tableService.loadTableMandatory(meta.inputTableId)
            probabilityClassColumns = trainResult.probabilityColumns.map { probabilityClassColumn =>
              Column(
                name = probabilityClassColumn.columnName,
                displayName = tabularModelCommonService.buildProbabilityColumnDisplayName(
                  probabilityClassColumn.className
                ),
                dataType = ColumnDataType.Double,
                variableType = ColumnVariableType.Continuous,
                align = tableService.getColumnAlignment(ColumnDataType.Double),
                statistics = None
              )
            }
            predictedColumn = tabularTrainPipelineHandler.buildPredictionResultColumn(
              meta.predictionResultColumnName,
              updatedModel.entity.responseColumn
            )
            createdOutputColumns = predictedColumn +: probabilityClassColumns
            outputTableColumns = createdOutputColumns ++ inputTable.entity.columns
            _ <- tableService.updateTable(
              meta.outputTableId,
              _.copy(
                columns = outputTableColumns,
                status = TableStatus.Active
              )
            )
            _ <- tableService.calculateColumnStatistics(
              tableId = meta.outputTableId,
              columns = Some(createdOutputColumns),
              userId = meta.userId
            )
            _ <- startEvaluations(
              meta,
              updatedModel,
              experiment,
              experimentResult
            )
          } yield ()
        case CortexJobStatus.Cancelled =>
          finishExperimentNonSuccessfully(meta, ExperimentStatus.Cancelled)
        case CortexJobStatus.Failed =>
          finishExperimentNonSuccessfully(meta, ExperimentStatus.Error)
      }
    } yield ()

  override protected def handleException(meta: Meta): Future[Unit] =
    finishExperimentNonSuccessfully(meta, ExperimentStatus.Error)

  private def finishExperimentNonSuccessfully[S <: ExperimentStatus: NonSuccessfulTerminalStatus](
    meta: Meta,
    status: S
  ): Future[Unit] = {
    val experimentResult = TabularTrainResult(
      modelId = meta.modelId,
      outputTableId = meta.outputTableId,
      holdOutOutputTableId = meta.holdOutEvaluationParams.map(_.outputTableId),
      predictedColumnName = meta.predictionResultColumnName,
      outOfTimeOutputTableId = meta.outOfTimeEvaluationParams.map(_.outputTableId),
      classes = None,
      summary = None,
      holdOutSummary = None,
      outOfTimeSummary = None,
      predictorsSummary = Seq.empty
    )
    for {
      _ <- tabularTrainPipelineHandler.updateOutputEntitiesOnNoSuccess(
        result = experimentResult,
        status = status
      )
      _ <- experimentDao.update(
        meta.experimentId,
        _.copy(status = status, result = Some(experimentResult))
      )
    } yield ()
  }

  private def buildModelClass(probabilityClassColumn: ProbabilityClassColumn): TabularModelClass =
    TabularModelClass(
      className = probabilityClassColumn.className,
      probabilityColumnName = probabilityClassColumn.columnName
    )

  private def startEvaluations(
    meta: Meta,
    model: WithId[TabularModel],
    experiment: WithId[Experiment],
    experimentResult: TabularTrainResult
  ): Future[Unit] = {

    def runEvaluation(
      evaluationParams: TypedEvaluationParams,
      nextStepEvalParams: Option[TypedEvaluationParams]
    ): Future[Unit] =
      for {
        _ <- modelDao.update(model.id, _.copy(status = TabularModelStatus.Predicting))
        pipeline <- experimentCommonService.getExperimentPipelineAs[TabularTrainPipeline](experiment.entity).toFuture
        _ <- tabularTrainPipelineHandler.launchEvaluation(
          model = model,
          experimentId = experiment.id,
          samplingWeightColumnName = pipeline.samplingWeightColumnName,
          evaluationParams = evaluationParams,
          nextStepEvaluationParams = nextStepEvalParams,
          userId = meta.userId
        )
      } yield ()

    (meta.holdOutEvaluationParams, meta.outOfTimeEvaluationParams) match {
      case (Some(holdOutParams), outOfTimeParams) =>
        runEvaluation(
          TypedEvaluationParams(baseParams = holdOutParams, evaluationType = EvaluationType.HoldOut),
          outOfTimeParams.map(TypedEvaluationParams(_, EvaluationType.OutOfTime))
        )
      case (None, Some(outOfTimeParams)) =>
        runEvaluation(
          TypedEvaluationParams(baseParams = outOfTimeParams, evaluationType = EvaluationType.OutOfTime),
          None
        )
      case (None, None) =>
        for {
          _ <- experimentDao.update(experiment.id, _.copy(status = ExperimentStatus.Completed))
          _ <- tabularTrainPipelineHandler.updateOutputEntitiesOnSuccess(experimentResult)
        } yield ()
    }
  }

}

object TabularModelTrainResultHandler {

  case class Meta(
    modelId: String,
    inputTableId: String,
    outputTableId: String,
    holdOutEvaluationParams: Option[EvaluationParams],
    outOfTimeEvaluationParams: Option[EvaluationParams],
    experimentId: String,
    predictionResultColumnName: String,
    userId: UUID
  )

  private implicit val EvaluationParamsFormat: OFormat[EvaluationParams] = Json.format[EvaluationParams]

  implicit val TabularModelTrainResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

}
