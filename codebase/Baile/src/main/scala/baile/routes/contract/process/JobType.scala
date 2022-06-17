package baile.routes.contract.process

import baile.utils.json.EnumFormatBuilder
import play.api.libs.json.Format

sealed trait JobType

object JobType {

  case object S3VideoImport extends JobType

  case object S3ImagesImport extends JobType

  case object TabularUpload extends JobType

  case object AlbumAugmentation extends JobType

  case object CVModelTrain extends JobType

  case object CVModelPredict extends JobType

  case object CVModelEvaluate extends JobType

  case object TabularPredict extends JobType

  case object TabularTrain extends JobType

  case object TabularEvaluate extends JobType

  case object TabularColumnStatistics extends JobType

  case object TabularModelImport extends JobType

  case object CVModelImport extends JobType

  case object MergeAlbum extends JobType

  case object ProjectBuild extends JobType

  case object GenericExperiment extends JobType

  case object DatasetImport extends JobType

  case object DatasetExport extends JobType


  // added here since we can't guarantee all handlers have job type assigned at compile time
  case object Unknown extends JobType

  implicit val JobTypeFormat: Format[JobType] = EnumFormatBuilder.build(
    {
      case "S3_VIDEO_IMPORT" => S3VideoImport
      case "S3_IMAGES_IMPORT" => S3ImagesImport
      case "TABULAR_UPLOAD" => TabularUpload
      case "ALBUM_AUGMENTATION" => AlbumAugmentation
      case "CV_MODEL_TRAIN" => CVModelTrain
      case "CV_MODEL_EVALUATE" => CVModelEvaluate
      case "CV_MODEL_PREDICT" => CVModelPredict
      case "TABULAR_EVALUATE" => TabularEvaluate
      case "TABULAR_TRAIN" => TabularTrain
      case "TABULAR_PREDICT" => TabularPredict
      case "TABULAR_MODEL_IMPORT" => TabularModelImport
      case "CV_MODEL_IMPORT" => CVModelImport
      case "TABULAR_COLUMN_STATISTICS" => TabularColumnStatistics
      case "MERGE_ALBUM" => MergeAlbum
      case "PROJECT_BUILD" => ProjectBuild
      case "GENERIC_EXPERIMENT" => GenericExperiment
      case "DATASET_IMPORT" => DatasetImport
      case "DATASET_EXPORT" => DatasetExport
      case "UNKNOWN" => Unknown
    },
    {
      case S3VideoImport => "S3_VIDEO_IMPORT"
      case S3ImagesImport => "S3_IMAGES_IMPORT"
      case TabularUpload => "TABULAR_UPLOAD"
      case AlbumAugmentation => "ALBUM_AUGMENTATION"
      case CVModelTrain => "CV_MODEL_TRAIN"
      case CVModelEvaluate => "CV_MODEL_EVALUATE"
      case CVModelPredict => "CV_MODEL_PREDICT"
      case TabularEvaluate => "TABULAR_EVALUATE"
      case TabularTrain => "TABULAR_TRAIN"
      case TabularPredict => "TABULAR_PREDICT"
      case TabularModelImport => "TABULAR_MODEL_IMPORT"
      case CVModelImport => "CV_MODEL_IMPORT"
      case TabularColumnStatistics => "TABULAR_COLUMN_STATISTICS"
      case MergeAlbum => "MERGE_ALBUM"
      case ProjectBuild => "PROJECT_BUILD"
      case GenericExperiment => "GENERIC_EXPERIMENT"
      case DatasetImport => "DATASET_IMPORT"
      case DatasetExport => "DATASET_EXPORT"
      case Unknown => "UNKNOWN"
    },
    "job type"
  )
}
