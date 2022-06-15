package baile.routes.contract

import baile.domain.process.ProcessStatus
import baile.routes.contract.process.JobType._
import baile.services.cv.model._
import baile.services.cv.prediction.CVPredictionResultHandler
import baile.services.dataset.{
  DatasetExportToS3ResultHandler,
  DatasetImportFromS3ResultHandler
}
import baile.services.dcproject.DCProjectBuildResultHandler
import baile.services.images._
import baile.services.pipeline.PipelineJobResultHandler
import baile.services.table.{ ColumnStatisticsResultHandler, TableUploadResultHandler }
import baile.services.tabular.model.{
  TabularModelEvaluateResultHandler,
  TabularModelImportResultHandler,
  TabularModelTrainResultHandler
}
import baile.services.tabular.prediction.TabularPredictionResultHandler
import baile.utils.json.EnumWritesBuilder
import play.api.libs.json._

package object process {

  implicit val ProcessStatusWrites: Writes[ProcessStatus] = EnumWritesBuilder.build {
    case ProcessStatus.Queued => "QUEUED"
    case ProcessStatus.Running => "RUNNING"
    case ProcessStatus.Completed => "COMPLETED"
    case ProcessStatus.Failed => "FAILED"
    case ProcessStatus.Cancelled => "CANCELLED"
  }

  val JobTypeToHandlerClassMap: Map[JobType, String] = Map(
    S3VideoImport -> classOf[VideoImportFromS3ResultHandler].getCanonicalName,
    S3ImagesImport -> classOf[ImagesImportFromS3ResultHandler].getCanonicalName,
    TabularUpload -> classOf[TableUploadResultHandler].getCanonicalName,
    AlbumAugmentation -> classOf[ImagesAugmentationResultHandler].getCanonicalName,
    CVModelTrain -> classOf[CVModelTrainResultHandler].getCanonicalName,
    CVModelEvaluate -> classOf[CVModelEvaluateResultHandler].getCanonicalName,
    CVModelPredict -> classOf[CVPredictionResultHandler].getCanonicalName,
    TabularEvaluate -> classOf[TabularModelEvaluateResultHandler].getCanonicalName,
    TabularTrain -> classOf[TabularModelTrainResultHandler].getCanonicalName,
    TabularPredict -> classOf[TabularPredictionResultHandler].getCanonicalName,
    TabularModelImport -> classOf[TabularModelImportResultHandler].getCanonicalName,
    TabularColumnStatistics -> classOf[ColumnStatisticsResultHandler].getCanonicalName,
    CVModelImport -> classOf[CVModelImportResultHandler].getCanonicalName,
    MergeAlbum -> classOf[MergeAlbumsResultHandler].getCanonicalName,
    ProjectBuild -> classOf[DCProjectBuildResultHandler].getCanonicalName,
    GenericExperiment -> classOf[PipelineJobResultHandler].getCanonicalName,
    DatasetImport -> classOf[DatasetImportFromS3ResultHandler].getCanonicalName,
    DatasetExport -> classOf[DatasetExportToS3ResultHandler].getCanonicalName
    // Unknown should not be here, so user can't filter by it
  )

  val HandlerClassToJobTypeMap: Map[String, JobType] = JobTypeToHandlerClassMap.map(_.swap)
    .withDefaultValue(Unknown)

}
