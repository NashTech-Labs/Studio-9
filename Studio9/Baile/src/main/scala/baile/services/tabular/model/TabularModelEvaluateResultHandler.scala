package baile.services.tabular.model

import java.util.UUID

import baile.dao.experiment.ExperimentDao
import baile.dao.tabular.model.TabularModelDao
import baile.daocommons.WithId
import baile.domain.experiment.{ Experiment, ExperimentStatus }
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.domain.tabular.pipeline.TabularTrainPipeline
import baile.domain.tabular.result.TabularTrainResult
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.experiment.ExperimentCommonService
import baile.services.process.JobResultHandler
import baile.services.table.TableService
import baile.services.tabular.model.TabularModelEvaluateResultHandler.Meta
import baile.services.tabular.model.TabularTrainPipelineHandler.{
  EvaluationParams,
  EvaluationType,
  NonSuccessfulTerminalStatus,
  TypedEvaluationParams
}
import baile.utils.TryExtensions._
import cortex.api.job.tabular.EvaluationResult
import play.api.libs.json._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class TabularModelEvaluateResultHandler(
  val tabularModelCommonService: TabularModelCommonService,
  val tabularTrainPipelineHandler: TabularTrainPipelineHandler,
  val tableService: TableService,
  val experimentDao: ExperimentDao,
  val experimentCommonService: ExperimentCommonService,
  val cortexJobService: CortexJobService,
  val jobMetaService: JobMetaService,
  val modelDao: TabularModelDao
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] =
    TabularModelEvaluateResultHandler.TabularModelEvaluateResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] =
    for {
      experiment <- experimentCommonService.loadExperimentMandatory(meta.experimentId)
      _ <- lastStatus match {
        case CortexJobStatus.Completed =>
          for {
            outputPath <- cortexJobService.getJobOutputPath(jobId)
            rawJobResult <- jobMetaService.readRawMeta(jobId, outputPath)
            evaluationResult <- Try(EvaluationResult.parseFrom(rawJobResult)).toFuture
            experimentResult <- getExperimentResultMandatory(experiment)
            newExperimentResult = meta.evaluationType match {
              case EvaluationType.HoldOut => experimentResult.copy(
                holdOutSummary = Some(
                  tabularModelCommonService.buildTabularModelEvaluationSummary(evaluationResult.summary)
                )
              )
              case EvaluationType.OutOfTime => experimentResult.copy(
                outOfTimeSummary = Some(
                  tabularModelCommonService.buildTabularModelEvaluationSummary(evaluationResult.summary)
                )
              )
            }
            _ <- experimentDao.update(id = experiment.id, _.copy(result = Some(newExperimentResult)))
            model <- tabularModelCommonService.loadModelMandatory(newExperimentResult.modelId)
            _ <- tableService.calculateColumnStatistics(
              tableId = meta.outputTableId,
              columns = None,
              userId = meta.userId
            )
            _ <- meta.nextStepParams match {
              case Some(params) =>
                for {
                  pipeline <- experimentCommonService.getExperimentPipelineAs[TabularTrainPipeline](
                    experiment.entity
                  ).toFuture
                  _ <- tabularTrainPipelineHandler.launchEvaluation(
                    model = model,
                    experimentId = experiment.id,
                    evaluationParams = params,
                    nextStepEvaluationParams = None,
                    samplingWeightColumnName = pipeline.samplingWeightColumnName,
                    userId = meta.userId
                  )
                } yield ()
              case None =>
                for {
                  _ <- experimentDao.update(experiment.id, _.copy(status = ExperimentStatus.Completed))
                  _ <- tabularTrainPipelineHandler.updateOutputEntitiesOnSuccess(experimentResult)
                } yield ()
            }
          } yield ()
        case CortexJobStatus.Cancelled =>
          finishExperimentNonSuccessfully(experiment, ExperimentStatus.Cancelled)
        case CortexJobStatus.Failed =>
          finishExperimentNonSuccessfully(experiment, ExperimentStatus.Error)
      }
    } yield ()

  override protected def handleException(meta: Meta): Future[Unit] =
    for {
      experiment <- experimentCommonService.loadExperimentMandatory(meta.experimentId)
      _ <- finishExperimentNonSuccessfully(experiment, ExperimentStatus.Error)
    } yield ()

  private def finishExperimentNonSuccessfully[S <: ExperimentStatus: NonSuccessfulTerminalStatus](
    experiment: WithId[Experiment],
    status: S
  ): Future[Unit] =
    for {
      experimentResult <- getExperimentResultMandatory(experiment)
      _ <- tabularTrainPipelineHandler.updateOutputEntitiesOnNoSuccess(
        experimentResult,
        ExperimentStatus.Error
      )
      _ <- experimentDao.update(
        experiment.id,
        _.copy(status = status)
      )
    } yield ()

  private def getExperimentResultMandatory(experiment: WithId[Experiment]): Future[TabularTrainResult] =
    experimentCommonService.getExperimentResultAs[TabularTrainResult](experiment.entity).map(_.getOrElse(
      throw new RuntimeException(
        s"Unexpectedly not found result for experiment ${ experiment.id } during evaluation handling"
      )
    )).toFuture

}

object TabularModelEvaluateResultHandler {

  case class Meta(
    experimentId: String,
    evaluationType: EvaluationType,
    nextStepParams: Option[TypedEvaluationParams],
    outputTableId: String,
    userId: UUID
  )

  private implicit val EvaluationTypeFormat: Format[EvaluationType] = new Format[EvaluationType] {

    override def writes(value: EvaluationType): JsValue = JsString {
      value match {
        case EvaluationType.HoldOut => "holdOut"
        case EvaluationType.OutOfTime => "outOfTime"
      }
    }

    override def reads(json: JsValue): JsResult[EvaluationType] = json match {
      case JsString(value) => value match {
        case "holdOut" => JsSuccess(EvaluationType.HoldOut)
        case "outOfTime" => JsSuccess(EvaluationType.OutOfTime)
        case unknown => JsError(s"Unknown value $unknown for evaluation type")
      }
      case _ => JsError("Expected js string for evaluation type value")
    }

  }
  private implicit val EvaluationParamsFormat: OFormat[EvaluationParams] = Json.format[EvaluationParams]
  private implicit val TypedEvaluationParamsFormat: OFormat[TypedEvaluationParams] = Json.format[TypedEvaluationParams]

  implicit val TabularModelEvaluateResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]


}
