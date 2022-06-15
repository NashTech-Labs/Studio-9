package baile.services.cv.model

import java.util.UUID

import baile.dao.experiment.ExperimentDao
import baile.daocommons.WithId
import baile.domain.cv.EvaluateTimeSpentSummary
import baile.domain.cv.model.{ CVModelStatus, CVModelSummary }
import baile.domain.cv.result.CVTLTrainResult
import baile.domain.experiment.{ Experiment, ExperimentStatus }
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.cv.model.CVModelEvaluateResultHandler.{ Meta, StepOne, StepTwo }
import baile.services.cv.model.CVModelTrainPipelineHandler.NonSuccessfulTerminalStatus
import baile.services.experiment.ExperimentCommonService
import baile.services.process.JobResultHandler
import baile.utils.TryExtensions._
import baile.utils.json.CommonFormats._
import cats.data.NonEmptyList
import cortex.api.job.computervision.EvaluateResult
import play.api.libs.json.{ Json, OFormat, Reads }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class CVModelEvaluateResultHandler(
  experimentDao: ExperimentDao,
  experimentCommonService: ExperimentCommonService,
  cvModelTrainPipelineHandler: CVModelTrainPipelineHandler,
  cortexJobService: CortexJobService,
  jobMetaService: JobMetaService,
  cvModelCommonService: CVModelCommonService
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] = CVModelEvaluateResultHandler.CVModelEvaluateResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] = {
    val currentStepMeta = meta.stepsMeta.head
    val nextStepsMeta = meta.stepsMeta.tail
    for {
      model <- cvModelCommonService.loadModelMandatory(currentStepMeta.modelId)
      _ <- cvModelCommonService.assertModelStatus(model, CVModelStatus.Predicting).toFuture
      _ <- lastStatus match {
        case CortexJobStatus.Completed =>
          for {
            experiment <- experimentCommonService.loadExperimentMandatory(currentStepMeta.experimentId)
            oldExperimentResult <- getExperimentResultMandatory(experiment)
            outputPath <- cortexJobService.getJobOutputPath(jobId)
            cortexJobTimeSummary <- cortexJobService.getJobTimeSummary(jobId)
            rawJobResult <- jobMetaService.readRawMeta(jobId, outputPath)
            evaluateResult <- Try(EvaluateResult.parseFrom(rawJobResult)).toFuture
            testSummary = CVModelSummary(
              labels = evaluateResult.confusionMatrix.fold[Seq[String]](Seq.empty)(_.labels),
              confusionMatrix = evaluateResult.confusionMatrix.map(_.confusionMatrixCells.map(
                CVModelCommonService.buildConfusionMatrixCell
              )),
              mAP = evaluateResult.map,
              reconstructionLoss = None
            )
            evaluateTimeSpentSummary = EvaluateTimeSpentSummary(
              dataFetchTime = evaluateResult.dataFetchTime,
              loadModelTime = evaluateResult.loadModelTime,
              scoreTime = evaluateResult.scoreTime,
              tasksQueuedTime = cortexJobTimeSummary.tasksQueuedTime,
              totalJobTime = cortexJobTimeSummary.calculateTotalJobTime,
              pipelineTimings = cortexJobService.buildPipelineTimings(evaluateResult.pipelineTimings)
            )
            (stepToUpdate, oldStepResult) = (oldExperimentResult.stepOne, oldExperimentResult.stepTwo) match {
              case (stepOne, _) if stepOne.modelId == currentStepMeta.modelId =>
                StepOne -> stepOne
              case (_, Some(stepTwo)) if stepTwo.modelId == currentStepMeta.modelId =>
                StepTwo -> stepTwo
              case _ =>
                throw new RuntimeException(
                  s"Unexpectedly not found model ${ model.id } in the result for experiment ${ experiment.id }"
                )
            }
            newStepResult = oldStepResult.copy(
              testSummary = Some(testSummary),
              evaluateTimeSpentSummary = Some(evaluateTimeSpentSummary)
            )
            newExperimentResult = stepToUpdate match {
              case StepOne => oldExperimentResult.copy(stepOne = newStepResult)
              case StepTwo => oldExperimentResult.copy(stepTwo = Some(newStepResult))
            }
            _ <- cvModelCommonService.populateOutputAlbumIfNeeded(
              inputAlbumId = currentStepMeta.testInputAlbumId,
              outputAlbumId = newStepResult.testOutputAlbumId,
              predictedImages = evaluateResult.images
            )
            _ <- cvModelCommonService.updatePredictionTableColumnsAndCalculateStatistics(
              probabilityPredictionTableId = newStepResult.testProbabilityPredictionTableId,
              probabilityPredictionTableSchema = evaluateResult.probabilityPredictionTableSchema,
              modelType = model.entity.`type`,
              userId = currentStepMeta.userId
            )
            _ <- experimentDao.update(experiment.id, _.copy(result = Some(newExperimentResult)))
            _ <- nextStepsMeta match {
              case Nil =>
                for {
                  _ <- cvModelTrainPipelineHandler.updateOutputEntitiesOnSuccess(newExperimentResult)
                  _ <- experimentDao.update(experiment.id, _.copy(status = ExperimentStatus.Completed))
                } yield ()
              case nextStepMeta :: rest =>
                for {
                  _ <- cvModelTrainPipelineHandler.launchEvaluation(nextStepMeta, rest)
                  _ <- cvModelCommonService.updateModelStatus(nextStepMeta.modelId, CVModelStatus.Predicting)
                } yield ()
            }
          } yield ()
        case CortexJobStatus.Cancelled =>
          for {
            experiment <- experimentDao.get(currentStepMeta.experimentId)
            _ <- experiment match {
              case Some(experiment) => finishExperimentNonSuccessfully(experiment, ExperimentStatus.Cancelled)
              case None => Future.unit
            }
          } yield ()
        case CortexJobStatus.Failed =>
          for {
            experiment <- experimentDao.get(currentStepMeta.experimentId)
            _ <- experiment match {
              case Some(experiment) => finishExperimentNonSuccessfully(experiment, ExperimentStatus.Error)
              case None => Future.unit
            }
          } yield ()
      }
    } yield ()
  }

  private def finishExperimentNonSuccessfully[S <: ExperimentStatus: NonSuccessfulTerminalStatus](
    experiment: WithId[Experiment],
    status: S
  ): Future[Unit] =
    for {
      experimentResult <- getExperimentResultMandatory(experiment)
      _ <- cvModelTrainPipelineHandler.updateOutputEntitiesOnNoSuccess(
        experimentResult,
        ExperimentStatus.Error
      )
      _ <- experimentDao.update(
        experiment.id,
        _.copy(status = status)
      )
    } yield ()

  override protected def handleException(meta: Meta): Future[Unit] =
    for {
      experiment <- experimentCommonService.loadExperimentMandatory(meta.stepsMeta.head.experimentId)
      _ <- finishExperimentNonSuccessfully(experiment, ExperimentStatus.Error)
    } yield ()

  private def getExperimentResultMandatory(experiment: WithId[Experiment]): Future[CVTLTrainResult] =
    experimentCommonService.getExperimentResultAs[CVTLTrainResult](experiment.entity).map(_.getOrElse(
      throw new RuntimeException(
        s"Unexpectedly not found result for experiment ${ experiment.id } during evaluation handling"
      )
    )).toFuture

}

object CVModelEvaluateResultHandler {

  case class Meta(stepsMeta: NonEmptyList[StepMeta]) extends AnyVal

  case class StepMeta(
    modelId: String,
    experimentId: String,
    testInputAlbumId: String,
    userId: UUID,
    probabilityPredictionTableId: Option[String]
  )

  implicit val CVModelEvaluateResultHandlerStepMetaFormat: OFormat[StepMeta] = Json.format[StepMeta]

  implicit val CVModelEvaluateResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

  private sealed trait StepToUpdate

  private case object StepOne extends StepToUpdate
  private case object StepTwo extends StepToUpdate

}
