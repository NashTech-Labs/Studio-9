package cortex.jobmaster.orion.service.domain

import cortex.CortexException
import cortex.jobmaster.jobs.job.image_uploading.ImageFile
import cortex.jobmaster.jobs.job.image_uploading.ImageFilesSource.{ S3FilesSequence, S3FilesSource }
import cortex.jobmaster.jobs.job.image_uploading.ImageUploadingJob.ImageUploadingJobParams
import cortex.task.StorageAccessParams.S3AccessParams
import StringHelpers._

trait ImageUploadingBase {

  def prepareParams(
    s3AccessParams:          S3AccessParams,
    targetPrefix:            String,
    imagesPath:              Option[String],
    labelsCSVPath:           String                 = "",
    labelsCSVFile:           Array[Byte]            = Array.empty[Byte],
    imageFiles:              Option[Seq[ImageFile]] = None,
    applyLogTransformations: Boolean                = true
  ): ImageUploadingJobParams = {
    val labelsPath = {
      if (labelsCSVPath.nonEmpty) {
        Some(labelsCSVPath.removeTrailingSlashes())
      } else {
        None
      }
    }

    val labelsFile = {
      if (labelsCSVFile.nonEmpty) {
        Some(labelsCSVFile)
      } else {
        None
      }
    }

    val imageFilesSource = (imageFiles, imagesPath) match {
      case (Some(seq), None) => S3FilesSequence(seq)
      case (Some(seq), Some(path)) =>
        val basePathOption = path.removeTrailingSlashes() match {
          case ""     => None
          case string => Some(string)
        }
        S3FilesSequence(seq, basePathOption)
      case (None, Some(path)) => S3FilesSource(s3AccessParams, Some(path.removeTrailingSlashes()).filter(_.nonEmpty))
      case _ => throw new CortexException(s"error creating ImageUploadingParams," +
        s" imagesPath: $imagesPath, imageFiles: $imageFiles")
    }

    ImageUploadingJobParams(
      albumPath               = targetPrefix,
      imageFilesSource        = imageFilesSource,
      inS3AccessParams        = s3AccessParams,
      csvFileS3Path           = labelsPath,
      csvFileBytes            = labelsFile,
      applyLogTransformations = applyLogTransformations
    )
  }
}
