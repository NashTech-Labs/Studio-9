package baile.services.pipeline

import java.util.UUID

import baile.dao.experiment.ExperimentDao
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.common.ConfusionMatrixCell
import baile.domain.experiment.ExperimentStatus
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.domain.pipeline.result._
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.experiment.ExperimentCommonService
import baile.services.pipeline.PipelineJobResultHandler.Meta
import baile.services.process.JobResultHandler
import baile.utils.TryExtensions._
import cortex.api.job.common.{ ConfusionMatrixCell => CortexConfusionMatrixCell }
import cortex.api.job.pipeline
import cortex.api.job.pipeline.OperatorApplicationSummary.Summary
import cortex.api.job.pipeline.PipelineStepResponse.Response
import cortex.api.job.pipeline.PipelineValue.{ Value => CortexPipelineValue }
import cortex.api.job.pipeline.{
  OperatorApplicationSummary,
  PipelineRunResponse,
  PipelineValue,
  TrackedAssetReference,
  AssetType => CortexAssetType
}
import play.api.libs.json.{ Json, OFormat, Reads }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class PipelineJobResultHandler(
  experimentDao: ExperimentDao,
  experimentCommonService: ExperimentCommonService,
  cortexJobService: CortexJobService,
  jobMetaService: JobMetaService
) extends JobResultHandler[Meta] {
  override protected val metaReads: Reads[Meta] = PipelineJobResultHandler.PipelineStepResultHandlerMetaFormat

  // scalastyle:off method.length
  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] = {

    def toDomainGenericExperimentResult(pipelineRunResponse: PipelineRunResponse): GenericExperimentResult = {

      def toDomainAssetType(cortexAssetType: cortex.api.job.pipeline.AssetType) = {
        cortexAssetType match {
          case CortexAssetType.TabularModel => AssetType.TabularModel
          case CortexAssetType.TabularPrediction => AssetType.TabularPrediction
          case CortexAssetType.Table => AssetType.Table
          case CortexAssetType.Flow => AssetType.Flow
          case CortexAssetType.Album => AssetType.Album
          case CortexAssetType.CvModel => AssetType.CvModel
          case CortexAssetType.CvPrediction => AssetType.CvPrediction
          case CortexAssetType.OnlineJob => AssetType.OnlineJob
          case CortexAssetType.DCProject => AssetType.DCProject
          case CortexAssetType.Experiment => AssetType.Experiment
          case CortexAssetType.Pipeline => AssetType.Pipeline
          case CortexAssetType.Unrecognized(value) => throw new RuntimeException(s"Invalid asset type : $value")
        }
      }

      def toDomainAssetReference(trackedAssetReference: TrackedAssetReference): AssetReference = AssetReference(
        id = trackedAssetReference.assetId,
        `type` = toDomainAssetType(trackedAssetReference.assetType)
      )

      def toDomainPipelineResultParam(pipelineValue: PipelineValue): PipelineResultValue = {
        pipelineValue.value match {
          case CortexPipelineValue.BooleanValue(value) => PipelineResultValue.BooleanValue(value)
          case CortexPipelineValue.StringValue(value) => PipelineResultValue.StringValue(value)
          case CortexPipelineValue.IntValue(value) => PipelineResultValue.IntValue(value)
          case CortexPipelineValue.FloatValue(value) => PipelineResultValue.FloatValue(value)
          case CortexPipelineValue.Empty => throw new RuntimeException("Invalid Pipeline Value")
        }
      }

      def toDomainConfusionMatrixCells(confusionMatrixCell: CortexConfusionMatrixCell) = {
        ConfusionMatrixCell(
          confusionMatrixCell.actualLabelIndex,
          confusionMatrixCell.predictedLabelIndex,
          confusionMatrixCell.value
        )
      }

      def toDomainSummary(operatorApplicationSummary: OperatorApplicationSummary) = {
        operatorApplicationSummary.summary match {
          case Summary.ConfusionMatrix(confusionMatrix) =>
            ConfusionMatrix(
              confusionMatrixCells = confusionMatrix.confusionMatrixCells.map(toDomainConfusionMatrixCells),
              labels = confusionMatrix.labels
            )
          case Summary.SimpleSummary(simpleSummary) =>
            SimpleSummary(
              simpleSummary.values.mapValues(toDomainPipelineResultParam)
            )
          case Summary.Empty => throw new RuntimeException("Invalid Operator Application Summary")
        }
      }

      def toGenericExperimentStepResult(
        generalResponse: pipeline.PipelineStepGeneralResponse,
        errorMessage: Option[String]
      ): GenericExperimentStepResult = {
        GenericExperimentStepResult(
          id = generalResponse.stepId,
          assets = generalResponse.trackedAssetReferences.map(toDomainAssetReference),
          outputValues = generalResponse.outputValues.mapValues(toDomainPipelineResultParam),
          summaries = generalResponse.summaries.map(toDomainSummary),
          executionTime = generalResponse.stepExecutionTime,
          failureMessage = errorMessage
        )
      }

      val stepResults = pipelineRunResponse.pipelineStepsResponse.map { pipelineStepResponse =>
        pipelineStepResponse.response match {
          case Response.PipelineStepGeneralResponse(generalResponse) =>
            toGenericExperimentStepResult(generalResponse, None)
          case Response.PipelineStepFailureResponse(failureResponse) =>
            toGenericExperimentStepResult(failureResponse.getPipelineStepGeneralResponse, failureResponse.errorMessage)
          case Response.Empty => throw new RuntimeException("Invalid Pipeline Step")
        }
      }

      GenericExperimentResult(stepResults)
    }

    def isSuccessResponse(pipelineRunResponse: PipelineRunResponse): Boolean = {
      pipelineRunResponse.pipelineStepsResponse.forall(_.response.isPipelineStepGeneralResponse)
    }

    for {
      _ <- lastStatus match {
        case CortexJobStatus.Completed =>
          for {
            experiment <- experimentCommonService.loadExperimentMandatory(meta.experimentId)
            outputPath <- cortexJobService.getJobOutputPath(jobId)
            rawJobResult <- jobMetaService.readRawMeta(jobId, outputPath)
            pipelineRunResponse <- Try(PipelineRunResponse.parseFrom(rawJobResult)).toFuture
            status = if (isSuccessResponse(pipelineRunResponse)) ExperimentStatus.Completed else ExperimentStatus.Error
            genericPipelineResult = toDomainGenericExperimentResult(pipelineRunResponse)
            _ <- experimentDao.update(experiment.id, _.copy(
              result = Some(genericPipelineResult),
              status = status
            ))
          } yield ()
        case CortexJobStatus.Cancelled =>
          experimentDao.update(meta.experimentId, _.copy(
            status = ExperimentStatus.Cancelled
          )).map(_ => ())
        case CortexJobStatus.Failed =>
          experimentDao.update(meta.experimentId, _.copy(
            status = ExperimentStatus.Error
          )).map(_ => ())
      }
    } yield ()
  }

  override protected def handleException(meta: Meta): Future[Unit] =
    experimentDao.update(meta.experimentId, _.copy(
      status = ExperimentStatus.Error
    )).map(_ => ())

}

object PipelineJobResultHandler {

  case class Meta(experimentId: String)

  implicit val PipelineStepResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

}
