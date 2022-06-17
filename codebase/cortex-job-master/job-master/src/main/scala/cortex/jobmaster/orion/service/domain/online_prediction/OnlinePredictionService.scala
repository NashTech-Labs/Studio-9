package cortex.jobmaster.orion.service.domain.online_prediction

import java.io.ByteArrayOutputStream

import com.github.tototoshi.csv.{ CSVWriter, DefaultCSVFormat, QUOTE_ALL }
import cortex.api.job.JobRequest
import cortex.api.job.common.FailedFile
import cortex.api.job.online.prediction._
import cortex.common.future.FutureExtensions._
import cortex.common.logging.JMLoggerFactory
import cortex.jobmaster.jobs.job.computer_vision.ClassificationJob
import cortex.jobmaster.jobs.job.image_uploading.ImageUploadingJob.UploadedImage
import cortex.jobmaster.jobs.job.image_uploading.{ ImageFile, ImageUploadingJob }
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler._
import cortex.jobmaster.orion.service.domain.online_prediction.OnlinePredictionService.CSVFormats
import cortex.jobmaster.orion.service.domain.{ ImageUploadingBase, JobRequestPartialHandler }
import cortex.jobmaster.orion.service.io.{ Marshaller, S3ParamResultStorageFactory }
import cortex.scheduler.TaskScheduler
import cortex.task.common.ClassReference
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.computer_vision.ClassificationModule
import cortex.task.computer_vision.ClassificationParams.{ CVPredictTaskParams, PredictionResult }
import cortex.task.image_uploading.ImageUploadingModule

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Random, Try }

class OnlinePredictionService(
    imageUploadingJob:              ImageUploadingJob,
    computerVisionJob:              ClassificationJob,
    onlinePredictionResultsStorage: S3ParamResultStorageFactory,
    s3AccessParams:                 S3AccessParams,
    onlinePredictionConfig:         OnlinePredictionJobConfig,
    modelsBasePath:                 String
)(implicit executionContext: ExecutionContext) extends JobRequestPartialHandler with ImageUploadingBase {

  private val onlinePredictionResultMarshaller = new Marshaller[Array[Byte], Array[Byte]] {
    override def marshall(value: Array[Byte]): Array[Byte] = {
      value
    }
  }

  def onlinePredict(jobId: JobId, request: PredictRequest): Future[(PredictResponse, JobTimeInfo)] = {

    val predictResultF = for {
      imageUploadingParams <- Try {
        prepareParams(
          s3AccessParams = prepareS3AccessParams(request),
          targetPrefix   = request.targetPrefix,
          imagesPath     = None,
          imageFiles     = Some(request.images.map(x => ImageFile(x.key, x.size, None)))
        )
      }.toFuture
      (imgUploadingResult, jobTasksTimeInfo) <- imageUploadingJob.uploadImages(jobId, imageUploadingParams)
      predictResult <- {
        val imagePaths = imgUploadingResult.succeed.map(_.path)
        val predictTaskParams = CVPredictTaskParams(
          imagePaths                     = imagePaths,
          albumPath                      = request.targetPrefix,
          modelId                        = request.modelId,
          displayNames                   = None,
          referenceIds                   = Seq.empty,
          featureExtractorClassReference = ClassReference(None, "ml_lib.feature_extractors.backbones", "StackedAutoEncoder"), // TODO: from request
          classReference                 = ClassReference(None, "ml_lib.classifiers", "FCN1"), // TODO: from request
          modelsBasePath                 = modelsBasePath,
          outputS3Params                 = s3AccessParams,
          outputTableS3Path              = None
        )
        computerVisionJob.predict(jobId, predictTaskParams)
      }
    } yield {
      val filenameToPrediction = predictResult.predictions.map {
        case PredictionResult(filename, label, confidence) =>
          filename -> (label -> confidence)
      }.toMap
      val predictedImages = imgUploadingResult.succeed.map {
        case UploadedImage(name, _, meta, path, size, _) =>
          val (label, confidence) = filenameToPrediction(path)
          LabledImage(path, size, name, meta, label, confidence)
      }
      val failed = imgUploadingResult.failed.map(f => FailedFile(f.path, Some(f.reason)))
      (
        PredictResponse(
          images      = predictedImages,
          failedFiles = failed
        ),
        JobTimeInfo(predictResult.taskTimeInfo +: jobTasksTimeInfo)
      )
    }

    for {
      (result, jobTimeInfo) <- predictResultF
      s3CsvPath <- uploadLabels(jobId, request.modelId, request.targetPrefix, result.images)
    } yield {
      (result.copy(s3ResultsCsvPath = s3CsvPath), jobTimeInfo)
    }
  }

  protected def prepareS3AccessParams(request: PredictRequest): S3AccessParams = {
    val s3AccessParams = S3AccessParams(
      request.bucketName,
      request.awsAccessKey,
      request.awsSecretKey,
      request.awsRegion,
      Some(request.awsSessionToken)
    )
    s3AccessParams
  }

  protected def uploadLabels(
    jobId:         JobId,
    modelId:       String,
    albumPath:     String,
    labeledImages: Seq[LabledImage]
  ): Future[String] = {
    val entropy = Random.alphanumeric.filter(c => c.isLetter && c <= 'z').take(7).mkString.toLowerCase
    val rootPath = s"$jobId/online_prediction_csv_$entropy"
    val writer = onlinePredictionResultsStorage.createStorageWriter[Array[Byte]](Some(onlinePredictionResultMarshaller))
    val csvUploadsF = labeledImages
      .grouped(onlinePredictionConfig.maxPredictionsPerResultFile)
      .toList
      .zipWithIndex
      .map {
        case (images, part) =>
          Future {
            val outputStream = new ByteArrayOutputStream()
            val csvWriter = CSVWriter.open(outputStream)(CSVFormats)
            csvWriter.writeAll(getDefaultHeader() +: images.map(i => imageToSeq(jobId, modelId, albumPath, i)))
            csvWriter.close()
            writer.put(outputStream.toByteArray, s"$rootPath/output-$part.csv")
          }
      }
    Future.sequence(csvUploadsF).map(_ => {
      s"s3://${onlinePredictionResultsStorage.baseBucket}/${onlinePredictionResultsStorage.basePath}/$rootPath"
    })
  }

  private def getDefaultHeader(): Seq[String] = {
    Seq("job_id", "model_id", "album_path", "file_name", "file_path", "file_size", "label", "confidence")
  }

  private def imageToSeq(jobId: String, modelId: String, albumPath: String, labeledImage: LabledImage): Seq[Any] = {
    Seq(
      jobId,
      modelId,
      albumPath,
      labeledImage.fileName,
      labeledImage.filePath,
      labeledImage.fileSize,
      labeledImage.label,
      labeledImage.confidence
    )
  }

  override def handlePartial: PartialFunction[(JobId, JobRequest), JobResult] = {
    case (jobId, jobReq) if jobReq.`type` == cortex.api.job.JobType.OnlinePrediction =>
      val importRequest = PredictRequest.parseFrom(jobReq.payload.toByteArray)
      onlinePredict(jobId, importRequest)
  }
}

object OnlinePredictionService {

  object CSVFormats extends DefaultCSVFormat {
    override val quoting = QUOTE_ALL
  }

  def apply(
    scheduler:      TaskScheduler,
    s3AccessParams: S3AccessParams,
    storageFactory: S3ParamResultStorageFactory,
    settings:       SettingsModule
  )(implicit executionContext: ExecutionContext, loggerFactory: JMLoggerFactory): OnlinePredictionService = {
    val imageUploadingModule = new ImageUploadingModule
    val imageUploadingJob = new ImageUploadingJob(
      scheduler            = scheduler,
      imageUploadingModule = imageUploadingModule,
      imageUploadingConfig = settings.imageUploadingConfig,
      outputS3AccessParams = s3AccessParams
    )

    val cvClassificationModule = new ClassificationModule
    val cvClassificationJob = new ClassificationJob(
      scheduler               = scheduler,
      module                  = cvClassificationModule,
      classificationJobConfig = settings.classificationConfig
    )

    new OnlinePredictionService(
      imageUploadingJob              = imageUploadingJob,
      computerVisionJob              = cvClassificationJob,
      s3AccessParams                 = s3AccessParams,
      modelsBasePath                 = settings.modelsPath,
      onlinePredictionResultsStorage = storageFactory,
      onlinePredictionConfig         = settings.onlinePredictionConfig
    )
  }
}
