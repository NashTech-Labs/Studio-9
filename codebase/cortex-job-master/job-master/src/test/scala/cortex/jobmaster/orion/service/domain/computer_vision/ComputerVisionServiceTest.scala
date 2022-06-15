package cortex.jobmaster.orion.service.domain.computer_vision

import java.io.File
import java.util.UUID

import cortex.TaskResult
import cortex.api.job.common.ClassReference
import cortex.api.job.computervision._
import cortex.api.job.table.TableMeta
import cortex.task.TabularAccessParams
import cortex.api.job.project.`package`.ProjectPackageRequest
import cortex.jobmaster.jobs.job.TestUtils
import cortex.jobmaster.jobs.job.computer_vision._
import cortex.jobmaster.jobs.job.tabular.TableExporterJob
import cortex.jobmaster.modules.SettingsModule
import cortex.jobmaster.orion.service.domain.ProjectPackagerService
import cortex.jobmaster.orion.service.domain.fixtures.UploadedImages
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.computer_vision._
import cortex.testkit.{ FutureTestUtils, WithEventually, WithS3AndLocalScheduler }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ CancelAfterFailure, FlatSpec }
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ComputerVisionServiceTest extends FlatSpec
  with FutureTestUtils
  with WithEventually
  with WithS3AndLocalScheduler
  with UploadedImages
  with MockFactory
  with CancelAfterFailure
  with SettingsModule {

  lazy val s3AccessParams = S3AccessParams(
    bucket      = baseBucket,
    accessKey   = accessKey,
    secretKey   = secretKey,
    region      = "",
    endpointUrl = Some(fakeS3Endpoint)
  )
  val tabularAccessParams = TabularAccessParams.RedshiftAccessParams(
    hostname  = "localhost",
    port      = 1234,
    username  = "user",
    password  = "pass",
    database  = "test_db",
    s3IamRole = "db"
  )

  lazy val cvClassificationModule = new ClassificationModule
  lazy val cvClassificationJob = new ClassificationJob(
    scheduler               = taskScheduler,
    module                  = cvClassificationModule,
    classificationJobConfig = this.classificationConfig
  )

  lazy val stackedAutoencoderModule = new StackedAutoencoderModule
  lazy val autoencoderJob = new AutoencoderJob(
    scheduler            = taskScheduler,
    module               = stackedAutoencoderModule,
    autoencoderJobConfig = this.autoencoderConfig
  )

  lazy val cvLocalizationModule = new LocalizationModule
  lazy val cvLocalizationJob = new LocalizationJob(
    scheduler             = taskScheduler,
    module                = cvLocalizationModule,
    localizationJobConfig = this.localizationConfig
  )

  lazy val modelImportModule = new ModelImportModule
  lazy val modelImportJob = new ModelImportJob(
    scheduler            = taskScheduler,
    module               = modelImportModule,
    modelImportJobConfig = this.modelImportConfig
  )

  lazy val customModelModule = new CustomModelModule
  lazy val customModelJob = new CustomModelJob(
    scheduler            = taskScheduler,
    module               = customModelModule,
    customModelJobConfig = this.customModelJobConfig
  )
  val tableExporterJob: TableExporterJob = mock[TableExporterJob]

  lazy val cvService = new ComputerVisionService(
    classificationJob = cvClassificationJob,
    localizationJob   = cvLocalizationJob,
    autoencoderJob    = autoencoderJob,
    modelImportJob    = modelImportJob,
    customModelJob    = customModelJob,
    tableExporterJob  = tableExporterJob,
    s3AccessParams    = s3AccessParams,
    modelsBasePath    = this.modelsPath,
    baseTablesPath    = "tables"
  )
  lazy val projectPackagerService = ProjectPackagerService(taskScheduler, s3AccessParams, this)

  var modelId: String = _
  var feId: String = _
  var scaeModelId: String = _
  var packageLocation: String = _

  val baseProjectPath = "cortex-job-master/python_project"
  val probabilityPredictionTable = TableMeta(
    schema = "cv",
    name   = "probabilities"
  )

  override def beforeAll(): Unit = {
    super.beforeAll()

    //upload package files to fake s3
    new File("../test_data/python_project/football").listFiles().filter(_.isFile).toList.foreach { f =>
      TestUtils.copyToS3(fakeS3Client, baseBucket, s"$baseProjectPath/football/${f.getName}", f.getAbsolutePath)
    }

    val projectPackageRequest = ProjectPackageRequest(
      projectFilesPath = baseProjectPath,
      name             = "test-project",
      version          = "0.1",
      targetPrefix     = "cortex-job-master/output_path"
    )
    val (result, _) = projectPackagerService.pack(jobId, projectPackageRequest).await()
    packageLocation = result.packageLocation
  }

  "ComputerVisionService" should "train FE if FeatureExtractor id isn't defined and save probabilities to table" in {
    val jobId = this.jobId
    val csvFilePath = s"tables/$jobId/probabilities.csv"
    val trainRequest = CVModelTrainRequest(
      images                         = taggedImages,
      filePathPrefix                 = albumPath,
      featureExtractorClassReference = Some(ClassReference(
        packageLocation = None,
        className       = "StackedAutoEncoder",
        moduleName      = "ml_lib.feature_extractors.backbones"
      )),
      modelType                      = Some(TLModelType(TLModelType.Type.ClassifierType(ClassReference(
        packageLocation = None,
        className       = "FCN1",
        moduleName      = "ml_lib.classifiers"
      )))),
      tuneFeatureExtractor           = true,
      probabilityPredictionTable     = Some(probabilityPredictionTable)
    )

    (tableExporterJob.exportToTable _).expects(jobId, *, *, *, *, *).returns(
      Future.successful(TaskResult.Empty())
    )

    val (result, _) = cvService.train(jobId, trainRequest, testMode = true).await()

    val featureExtractorReference = result.featureExtractorReference
    featureExtractorReference should not be empty
    val cvModelReference = result.cvModelReference
    cvModelReference should not be empty
    result.images.size shouldBe taggedImages.size
    result.confusionMatrix.size shouldBe 1

    val table = new String(fakeS3Client.get(baseBucket, csvFilePath))
    table.lines.length shouldBe taggedImages.size + 1

    //NOTE: for next test: import. Don't delete
    feId = featureExtractorReference.get.id
    modelId = cvModelReference.get.id
  }

  it should "import a model" in {
    val importRequest = CVModelImportRequest(
      path      = buildMlEntityFilePath(modelId),
      modelType = Some(CVModelType(CVModelType.Type.TlModel(TLModel(
        modelType                      = Some(TLModelType(TLModelType.Type.ClassifierType(ClassReference(
          packageLocation = None,
          className       = "FCN1",
          moduleName      = "ml_lib.classifiers"
        )))),
        featureExtractorClassReference = Some(ClassReference(
          packageLocation = None,
          className       = "StackedAutoEncoder",
          moduleName      = "ml_lib.feature_extractors.backbones"
        ))
      ))))
    )
    val (result, _) = cvService.importModel(jobId, importRequest).await()
    result.featureExtractorReference shouldBe defined
    result.cvModelReference shouldBe defined

    //NOTE: for next tests: score and predict. Don't delete
    modelId = result.cvModelReference.get.id
  }

  it should "import a FE" in {
    val importRequest = CVModelImportRequest(
      path      = buildMlEntityFilePath(feId),
      modelType = Some(CVModelType(CVModelType.Type.TlModel(TLModel(
        modelType                      = Some(TLModelType(TLModelType.Type.ClassifierType(ClassReference(
          packageLocation = None,
          className       = "FCN1",
          moduleName      = "ml_lib.classifiers"
        )))),
        featureExtractorClassReference = Some(ClassReference(
          packageLocation = None,
          className       = "StackedAutoEncoder",
          moduleName      = "ml_lib.feature_extractors.backbones"
        ))
      )))),
      feOnly    = true
    )
    val (result, _) = cvService.importModel(jobId, importRequest).await()
    result.featureExtractorReference shouldBe defined
    result.cvModelReference should not be defined
  }

  it should "do score and save probabilities to table" in {
    val jobId = this.jobId
    val csvFilePath = s"tables/$jobId/probabilities.csv"
    val tlModel = TLModel(
      modelType                      = Some(TLModelType(TLModelType.Type.ClassifierType(ClassReference(
        packageLocation = None,
        className       = "FCN1",
        moduleName      = "ml_lib.classifiers"
      )))),
      featureExtractorClassReference = Some(ClassReference(
        packageLocation = None,
        className       = "StackedAutoEncoder",
        moduleName      = "ml_lib.feature_extractors.backbones"
      ))
    )
    val scoreRequest = EvaluateRequest(
      modelType                  = Some(CVModelType(CVModelType.Type.TlModel(tlModel))),
      modelId                    = modelId,
      images                     = taggedImages,
      filePathPrefix             = albumPath,
      probabilityPredictionTable = Some(probabilityPredictionTable)
    )

    (tableExporterJob.exportToTable _).expects(jobId, *, *, *, *, *).returns(
      Future.successful(TaskResult.Empty())
    )

    val (result, _) = cvService.evaluate(jobId, scoreRequest).await()
    result.images.size shouldBe taggedImages.size

    val table = new String(fakeS3Client.get(baseBucket, csvFilePath))
    table.lines.length shouldBe taggedImages.size + 1
  }

  it should "do predict and save probabilities to table" in {
    val jobId = this.jobId
    val csvFilePath = s"tables/$jobId/probabilities.csv"
    val tlModel = TLModel(
      modelType                      = Some(TLModelType(TLModelType.Type.ClassifierType(ClassReference(
        packageLocation = None,
        className       = "FCN1",
        moduleName      = "ml_lib.classifiers"
      )))),
      featureExtractorClassReference = Some(ClassReference(
        packageLocation = None,
        className       = "StackedAutoEncoder",
        moduleName      = "ml_lib.feature_extractors.backbones"
      ))
    )
    val predictRequest = PredictRequest(
      modelType                  = Some(CVModelType(CVModelType.Type.TlModel(tlModel))),
      modelId                    = modelId,
      images                     = unlabeledImages,
      filePathPrefix             = albumPath,
      probabilityPredictionTable = Some(probabilityPredictionTable)
    )

    (tableExporterJob.exportToTable _).expects(jobId, *, *, *, *, *).returns(
      Future.successful(TaskResult.Empty())
    )

    val (result, _) = cvService.predict(jobId, predictRequest).await()
    result.images.size shouldBe unlabeledImages.size

    val table = new String(fakeS3Client.get(baseBucket, csvFilePath))
    table.lines.length shouldBe taggedImages.size + 1
  }

  it should "train StackedAutoencoder if model type is STACKED" in {
    val trainRequest: CVModelTrainRequest = CVModelTrainRequest(
      images                         = taggedImages,
      filePathPrefix                 = albumPath,
      tuneFeatureExtractor           = true,
      modelType                      = Some(TLModelType(TLModelType.Type.AutoencoderType(ClassReference(
        packageLocation = None,
        moduleName      = "ml_lib.models",
        className       = "SCAEModel"
      )))),
      featureExtractorClassReference = Some(ClassReference(
        packageLocation = None,
        className       = "TestClass",
        moduleName      = "test_module.test_submodule"
      ))
    )

    val (result, _) = cvService.train(jobId, trainRequest, testMode = true).await()

    result.featureExtractorReference should not be empty
    result.cvModelReference should not be empty
    result.reconstructionLoss.foreach(_ should be > 0.0)

    //NOTE: for next tests: score and predict. Don't delete
    scaeModelId = result.cvModelReference.get.id
  }

  it should "do predict for StackedAutoencoder (not using modelFeatureExtractorType)" in {
    val tlModel = TLModel(
      modelType                      = Some(TLModelType(TLModelType.Type.AutoencoderType(ClassReference(
        packageLocation = None,
        moduleName      = "ml_lib.models",
        className       = "SCAEModel"
      )))),
      featureExtractorClassReference = Some(ClassReference(
        packageLocation = None,
        className       = "TestClass",
        moduleName      = "test_module.test_submodule"
      ))
    )
    val predictRequest = PredictRequest(
      modelType      = Some(CVModelType(CVModelType.Type.TlModel(tlModel))),
      modelId        = scaeModelId,
      images         = unlabeledImages,
      filePathPrefix = albumPath,
      targetPrefix   = Some(outputAlbumPath)
    )
    val (result, _) = cvService.predict(jobId, predictRequest).await()
    result.images.size shouldBe unlabeledImages.size
    result.images.flatMap(_.image).foreach(image => {
      image.fileSize shouldBe defined
      image.fileSize.get should be > 0L
    })
  }

  it should "do score for StackedAutoencoder (not using modelFeatureExtractorType)" in {
    val tlModel = TLModel(
      modelType                      = Some(TLModelType(TLModelType.Type.AutoencoderType(ClassReference(
        packageLocation = None,
        moduleName      = "ml_lib.models",
        className       = "SCAEModel"
      )))),
      featureExtractorClassReference = Some(ClassReference(
        packageLocation = None,
        className       = "TestClass",
        moduleName      = "test_module.test_submodule"
      ))
    )
    val scoreRequest = EvaluateRequest(
      modelType      = Some(CVModelType(CVModelType.Type.TlModel(tlModel))),
      modelId        = scaeModelId,
      images         = taggedImages,
      filePathPrefix = albumPath
    )
    val (result, _) = cvService.evaluate(jobId, scoreRequest).await()
    result.map.foreach(_ should be > 0.0)
  }

  it should "do predict for custom CV model" in {
    val classReference = ClassReference(
      packageLocation = Some(packageLocation),
      moduleName      = "football.test_custom_model",
      className       = "TestCustomModel"
    )
    val predictRequest = PredictRequest(
      modelType      = Some(CVModelType(CVModelType.Type.CustomModel(CustomModel(Some(classReference))))),
      modelId        = modelId,
      images         = unlabeledImages,
      filePathPrefix = albumPath,
      targetPrefix   = Some(outputAlbumPath)
    )
    val (result, _) = cvService.predict(jobId, predictRequest).await()

    result.images.size shouldBe unlabeledImages.size
    result.images.foreach { predictedImage =>
      predictedImage.image shouldBe defined
      predictedImage.predictedTags.length shouldBe 1
      predictedImage.predictedTags.foreach { predictedTag =>
        predictedTag.tag shouldBe defined
      }
    }
  }

  it should "do predict for custom CV model and save probabilities to the table" in {
    val jobId = this.jobId
    val csvFilePath = s"tables/$jobId/probabilities.csv"
    val classReference = ClassReference(
      packageLocation = Some(packageLocation),
      moduleName      = "football.test_custom_model",
      className       = "TestCustomModel"
    )
    val predictRequest = PredictRequest(
      modelType                  = Some(CVModelType(CVModelType.Type.CustomModel(CustomModel(Some(classReference))))),
      modelId                    = modelId,
      images                     = unlabeledImages,
      filePathPrefix             = albumPath,
      targetPrefix               = Some(outputAlbumPath),
      probabilityPredictionTable = Some(probabilityPredictionTable)
    )

    (tableExporterJob.exportToTable _).expects(jobId, *, *, *, *, *).returns(
      Future.successful(TaskResult.Empty())
    )

    val (result, _) = cvService.predict(jobId, predictRequest).await()

    result.images.size shouldBe unlabeledImages.size
    result.images.foreach { predictedImage =>
      predictedImage.image shouldBe defined
      predictedImage.predictedTags.length shouldBe 1
      predictedImage.predictedTags.foreach { predictedTag =>
        predictedTag.tag shouldBe defined
      }
    }

    val table = new String(fakeS3Client.get(baseBucket, csvFilePath))
    table.lines.length shouldBe taggedImages.size + 1
  }

  it should "do score for custom CV model" in {
    val classReference = ClassReference(
      packageLocation = Some(packageLocation),
      moduleName      = "football.test_custom_model",
      className       = "TestCustomModel"
    )
    val scoreRequest = EvaluateRequest(
      modelType      = Some(CVModelType(CVModelType.Type.CustomModel(CustomModel(Some(classReference))))),
      modelId        = modelId,
      images         = taggedImages,
      filePathPrefix = albumPath
    )
    val (result, _) = cvService.evaluate(jobId, scoreRequest).await()

    result.confusionMatrix shouldBe defined
    result.images.size shouldBe unlabeledImages.size
    result.images.foreach { predictedImage =>
      predictedImage.image shouldBe defined
      predictedImage.predictedTags.length shouldBe 1
      predictedImage.predictedTags.foreach { predictedTag =>
        predictedTag.tag shouldBe defined
      }
    }
  }

  private def jobId = {
    UUID.randomUUID().toString
  }

  private def buildMlEntityFilePath(mlEntityId: String) = {
    this.modelsPath + "/" + mlEntityId + ".pth"
  }
}
