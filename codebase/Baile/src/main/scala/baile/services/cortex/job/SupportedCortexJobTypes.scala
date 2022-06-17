package baile.services.cortex.job

import cortex.api.job.album.augmentation.AugmentationRequest
import cortex.api.job.album.uploading.{ S3ImagesImportRequest, S3VideoImportRequest }
import cortex.api.job.computervision.CVModelImportRequest
import cortex.api.job.dataset.{ S3DatasetExportRequest, S3DatasetImportRequest }
import cortex.api.job.pipeline.PipelineRunRequest
import cortex.api.job.project.`package`.ProjectPackageRequest
import cortex.api.job.table.{ TableUploadRequest, TabularColumnStatisticsRequest }
import cortex.api.job.{ computervision, tabular, JobType => ProtobufJobType }
import cortex.api.job.tabular.TabularModelImportRequest

object SupportedCortexJobTypes {

  trait SupportedCortexJobType[T] {
    val protobufJobType: ProtobufJobType
    val logString: String
  }

  private def createInstance[T](protobufJobType: ProtobufJobType, logString: String): SupportedCortexJobType[T] = {
    val pjt = protobufJobType
    val ls = logString
    new SupportedCortexJobType[T] {
      override val protobufJobType: ProtobufJobType = pjt
      override val logString: String = ls
    }
  }

  implicit val CVModelTrainRequestSupportedCortexJobType: SupportedCortexJobType[computervision.CVModelTrainRequest] =
    createInstance(ProtobufJobType.CVModelTrain, "CV MODEL TRAIN")

  implicit val CVPredictRequestSupportedCortexJobType: SupportedCortexJobType[computervision.PredictRequest] =
    createInstance(ProtobufJobType.CVPredict, "CV PREDICT")

  implicit val CVEvaluateRequestSupportedCortexJobType: SupportedCortexJobType[computervision.EvaluateRequest] =
    createInstance(ProtobufJobType.CVEvaluate, "CV EVALUATE")

  implicit val TabularTrainRequestSupportedCortexJobType: SupportedCortexJobType[tabular.TrainRequest] =
    createInstance(ProtobufJobType.TabularTrain, "TABULAR TRAIN")

  implicit val TabularPredictRequestSupportedCortexJobType: SupportedCortexJobType[tabular.PredictRequest] =
    createInstance(ProtobufJobType.TabularPredict, "TABULAR PREDICT")

  implicit val TabularEvaluateRequestSupportedCortexJobType: SupportedCortexJobType[tabular.EvaluateRequest] =
    createInstance(ProtobufJobType.TabularEvaluate, "TABULAR EVALUATE")

  implicit val S3ImagesImportRequestSupportedCortexJobType: SupportedCortexJobType[S3ImagesImportRequest] =
    createInstance(ProtobufJobType.S3ImagesImport, "S3 IMAGES IMPORT")

  implicit val S3VideoImportRequestSupportedCortexJobType: SupportedCortexJobType[S3VideoImportRequest] =
    createInstance(ProtobufJobType.S3VideoImport, "S3 VIDEO IMPORT")

  implicit val TableUploadRequestSupportedCortexJobType: SupportedCortexJobType[TableUploadRequest] =
    createInstance(ProtobufJobType.TabularUpload, "TABLE UPLOAD")

  implicit val AugmentationRequestSupportedCortexJobType: SupportedCortexJobType[AugmentationRequest] =
    createInstance(ProtobufJobType.AlbumAugmentation, "ALBUM AUGMENTATION")

  implicit val CVModelImportRequestSupportedCortexJobType: SupportedCortexJobType[CVModelImportRequest] =
    createInstance(ProtobufJobType.CVModelImport, "CV MODEL IMPORT")

  implicit val TabularModelImportRequestSupportedCortexJobType: SupportedCortexJobType[TabularModelImportRequest] =
    createInstance(ProtobufJobType.TabularModelImport, "TABULAR MODEL IMPORT")

  implicit val TabularColumnStatisticsRequestSupportedCortexJobType: SupportedCortexJobType[
    TabularColumnStatisticsRequest
  ] = createInstance(ProtobufJobType.TabularColumnStatistics, "TABULAR COLUMN STATISTICS")

  implicit val ProjectPackageRequestSupportedCortexJobType: SupportedCortexJobType[ProjectPackageRequest] =
    createInstance(ProtobufJobType.ProjectPackage, "PROJECT PACKAGE")

  implicit val PipelineRunRequestSupportedCortexJobType: SupportedCortexJobType[PipelineRunRequest] =
    createInstance(ProtobufJobType.Pipeline, "PIPELINE")

  implicit val S3DatasetImportRequestSupportedCortexJobType: SupportedCortexJobType[S3DatasetImportRequest] =
    createInstance(ProtobufJobType.S3DatasetImport, "S3 DATASET IMPORT")

  implicit val S3DatasetExportRequestSupportedCortexJobType: SupportedCortexJobType[S3DatasetExportRequest] =
    createInstance(ProtobufJobType.S3DatasetExport, "S3 DATASET EXPORT")
}
