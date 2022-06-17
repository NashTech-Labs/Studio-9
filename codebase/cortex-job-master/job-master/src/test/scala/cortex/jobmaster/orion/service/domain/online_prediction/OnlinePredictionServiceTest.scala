package cortex.jobmaster.orion.service.domain.online_prediction

import java.io.File
import java.net.URI
import java.util.UUID

import cortex.api.job.online.prediction.{ Image, PredictRequest }
import cortex.io.S3Client
import cortex.jobmaster.jobs.job.TestUtils
import cortex.jobmaster.jobs.job.computer_vision.{ AutoencoderJob, ClassificationJob }
import cortex.jobmaster.jobs.job.image_uploading.ImageFilesSource.S3FilesSource
import cortex.jobmaster.jobs.job.image_uploading.ImageUploadingJob
import cortex.jobmaster.jobs.job.image_uploading.ImageUploadingJob.ImageUploadingJobParams
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.io.S3ParamResultStorageFactory
import cortex.task.common.ClassReference
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.computer_vision.AutoencoderParams.AutoencoderTrainTaskParams
import cortex.task.computer_vision.ClassificationParams.CVTrainTaskParams
import cortex.task.computer_vision.{ ClassificationModule, StackedAutoencoderModule }
import cortex.task.image_uploading.ImageUploadingModule
import cortex.testkit.{ FutureTestUtils, WithEventually, WithLogging, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class OnlinePredictionServiceTest extends FlatSpec
  with FutureTestUtils
  with WithEventually
  with WithS3AndLocalScheduler
  with WithLogging
  with SettingsModule {

  def albumPath(): String = s"cortex-job-master/e2e/test_albums/test_album-${UUID.randomUUID().toString}"

  var modelId: String = _
  val baseSarPath = "cortex-job-master/e2e/sar_sample"
  val onlinePredictionResultsBasePath = "some/online/prediction/path"
  val modelsBasePath = "some/models/base/path"

  lazy val s3AccessParams = S3AccessParams(
    bucket      = baseBucket,
    accessKey   = accessKey,
    secretKey   = secretKey,
    region      = "",
    endpointUrl = Some(fakeS3Endpoint)
  )
  val imageUploadingModule = new ImageUploadingModule
  lazy val imageUploadingJob: ImageUploadingJob = new ImageUploadingJob(
    scheduler            = taskScheduler,
    imageUploadingModule = imageUploadingModule,
    imageUploadingConfig = imageUploadingConfig,
    outputS3AccessParams = s3AccessParams
  ) {
    override protected def getS3Client(s3AccessCredentials: S3AccessParams): S3Client = {
      new S3Client(
        accessKey    = s3AccessCredentials.accessKey,
        secretKey    = s3AccessCredentials.secretKey,
        sessionToken = None,
        region       = "",
        endpointUrl  = Some(fakeS3Endpoint)
      )
    }
  }
  val computerVisionModule = new ClassificationModule
  val scaeEncoderEstimatorModule = new StackedAutoencoderModule
  lazy val cvJob = new ClassificationJob(taskScheduler, computerVisionModule, classificationConfig)
  lazy val feJob = new AutoencoderJob(taskScheduler, scaeEncoderEstimatorModule, autoencoderConfig)
  lazy val s3ParamsResultsStorage = new S3ParamResultStorageFactory(
    client     = fakeS3Client,
    baseBucket = baseBucket,
    basePath   = onlinePredictionResultsBasePath
  )
  lazy val onlinePredictionService: OnlinePredictionService = new OnlinePredictionService(
    imageUploadingJob              = imageUploadingJob,
    computerVisionJob              = cvJob,
    s3AccessParams                 = s3AccessParams,
    modelsBasePath                 = modelsBasePath,
    onlinePredictionConfig         = OnlinePredictionJobConfig(3),
    onlinePredictionResultsStorage = s3ParamsResultsStorage
  ) {
    override protected def prepareS3AccessParams(request: PredictRequest): S3AccessParams = {
      S3AccessParams(
        bucket       = request.bucketName,
        accessKey    = request.awsAccessKey,
        secretKey    = request.awsSecretKey,
        region       = request.awsRegion,
        sessionToken = Some(request.awsSessionToken),
        endpointUrl  = Some(fakeS3Endpoint)
      )
    }
  }
  var imageFiles: Seq[Image] = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    val concreteAlbPath = albumPath()

    //upload sar images to fake s3
    imageFiles = new File("../test_data/sar_sample").listFiles().filter(_.isFile).toList.map { f =>
      val imagePath = s"$baseSarPath/${f.getName}"
      TestUtils.copyToS3(fakeS3Client, baseBucket, imagePath, f.getAbsolutePath)
      Image(imagePath, 1L)
    }

    //upload images to an album
    val imageFilesSource = S3FilesSource(s3AccessParams, Some(baseSarPath))
    val params = ImageUploadingJobParams(
      albumPath               = concreteAlbPath,
      inS3AccessParams        = s3AccessParams,
      imageFilesSource        = imageFilesSource,
      csvFileS3Path           = None,
      csvFileBytes            = None,
      applyLogTransformations = true
    )
    val (imageUploadingResult, _) = imageUploadingJob.uploadImages(UUID.randomUUID().toString, params).await()

    val labeledImages = imageUploadingResult.succeed.map(_.path)
    val labels = imageUploadingResult.succeed.map(_.labels.head)
    val referenceIds = imageUploadingResult.succeed.map(img => Some(img.path))

    //train feature extractor
    val trainRequest = AutoencoderTrainTaskParams(
      albumPath                      = concreteAlbPath,
      imagePaths                     = labeledImages,
      referenceIds                   = referenceIds,
      modelsBasePath                 = modelsBasePath,
      outputS3Params                 = s3AccessParams,
      testMode                       = true,
      augmentationParams             = None,
      featureExtractorClassReference = ClassReference(None, "ml_lib.feature_extractors.backbones", "StackedAutoEncoder"),
      classReference                 = ClassReference(None, "ml_lib.models", "SCAEModel")
    )
    val featureExtractorId = feJob.train(UUID.randomUUID().toString, trainRequest).await().featureExtractorId

    //train model
    val trainTaskParams = CVTrainTaskParams(
      albumPath                      = concreteAlbPath,
      imagePaths                     = labeledImages,
      labels                         = labels,
      tuneFeatureExtractor           = false,
      displayNames                   = None,
      referenceIds                   = referenceIds,
      modelsBasePath                 = modelsBasePath,
      outputS3Params                 = s3AccessParams,
      classReference                 = ClassReference(None, "ml_lib.classifiers", "FCN1"),
      testMode                       = true,
      featureExtractorId             = Some(featureExtractorId),
      featureExtractorClassReference = ClassReference(None, "ml_lib.feature_extractors.backbones", "StackedAutoEncoder"),
      augmentationParams             = None,
      outputTableS3Path              = None
    )
    val result = cvJob.train(UUID.randomUUID().toString, trainTaskParams).await()

    //capture model id
    modelId = result.modelId
  }

  "Online prediction service" should "make online prediction" in {
    val concreteAlbPath = albumPath()
    val predictRequest = PredictRequest(
      modelId         = modelId,
      bucketName      = baseBucket,
      awsRegion       = "",
      awsAccessKey    = accessKey,
      awsSecretKey    = secretKey,
      awsSessionToken = "",
      targetPrefix    = concreteAlbPath,
      images          = imageFiles
    )
    val (results, _) = onlinePredictionService.onlinePredict(UUID.randomUUID().toString, predictRequest).await()

    val relativeUrl = new URI(results.s3ResultsCsvPath).getPath.drop(1)
    val files = fakeS3Client.getFiles(baseBucket, Some(relativeUrl))
    files.size shouldBe 4
    results.images.size shouldBe 10
    results.images.map(_.label).forall(_.nonEmpty) shouldBe true
    results.images.map(_.confidence).forall(_ > 0) shouldBe true
    results.failedFiles.size shouldBe 2
  }
}
