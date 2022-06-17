package cortex.api.job

import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.{ Date, UUID }

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectMetadata
import com.trueaccord.scalapb.GeneratedMessage
import com.typesafe.config.{ Config, ConfigFactory }
import cortex.api.job.album.common.{ Image, TaggedImage, Tag }
import cortex.api.job.album.uploading
import cortex.api.job.computervision._
import cortex.api.job.common._

class MessageGenerator(config: Config) {
  private val s3Client = AmazonS3Client.builder()
    .withCredentials(
      new AWSStaticCredentialsProvider(
        new BasicAWSCredentials(config.getString("aws.accessKey"), config.getString("aws.secretKey"))
      )
    )
    .withRegion(config.getString("aws.region"))
    .build()

  private val bucketName = config.getString("aws.bucketName")

  private lazy val taggedImages: Seq[TaggedImage] = {
    import collection.JavaConverters.collectionAsScalaIterableConverter
    val configObjects = config.getObjectList("cv.images")
    configObjects.asScala.map { configObject =>
      val config = configObject.toConfig
      TaggedImage(
        image = Some(Image(filePath = config.getString("filePath"))),
        tags = Seq(Tag(label = config.getString("label")))
      )
    }.toSeq
  }
  private lazy val filePathPrefix: String = config.getString("cv.filePathPrefix")
  private lazy val cvModelId: String = config.getString("cv.modelId")

  val modelType = TLModelType(TLModelType.Type.ClassifierType(ClassReference(
    packageLocation = Some("/packages/ml-lib/ml_lib-0.0.1-py3-none-any.whl"),
    moduleName = "ml_lib.classifiers.kpca_mnl.models.kpca_mnl",
    className = "vgg16"
  )))

  val featureExtractorClassReference = ClassReference(
    packageLocation = Some("/packages/ml-lib/ml_lib-0.0.1-py3-none-any.whl"),
    moduleName = "ml_lib.feature_extractors.backbones",
    className = "scae"
  )

  def generateCVTrain(outputPath: String): Unit = buildAndSaveRequest(
    computervision.CVModelTrainRequest(
      Some("fe_id"),
      Some(featureExtractorClassReference),
      taggedImages,
      filePathPrefix,
      Some(modelType),
      None,
      true
    ),
    outputPath
  )

  def generateCVPredict(outputPath: String): Unit = buildAndSaveRequest(
    computervision.PredictRequest(
      Some(CVModelType(CVModelType.Type.TlModel(TLModel(Some(modelType))))),
      cvModelId,
      taggedImages.map(_.image.get),
      filePathPrefix
    ),
    outputPath
  )

  def generateCVEvaluate(outputPath: String): Unit = buildAndSaveRequest(
    computervision.EvaluateRequest(
      Some(CVModelType(CVModelType.Type.TlModel(TLModel(Some(modelType))))),
      cvModelId,
      taggedImages,
      filePathPrefix
    ),
    outputPath
  )

  private def buildAndSaveRequest(request: GeneratedMessage, outputPath: String) = {

    val jobType = request match {
      case _: computervision.CVModelTrainRequest => JobType.CVModelTrain
      case _: computervision.PredictRequest => JobType.CVPredict
      case _: computervision.EvaluateRequest => JobType.CVEvaluate
      case _: tabular.TrainRequest => JobType.TabularTrain
      case _: tabular.PredictRequest => JobType.TabularPredict
      case _: uploading.S3ImagesImportRequest => JobType.S3ImagesImport
      case _: project.`package`.ProjectPackageRequest => JobType.ProjectPackage
      case _ => throw new Exception(s"Tried to construct job object on unsupported request type: $request")
    }

    val jobRequest = JobRequest(`type` = jobType, payload = request.toByteString)
    val serializedRequest = jobRequest.toByteArray

    val stream = new ByteArrayInputStream(serializedRequest)
    val meta = new ObjectMetadata()
    meta.setContentLength(serializedRequest.length)

    s3Client.putObject(bucketName, outputPath, stream, meta)
  }

}


object MessageGenerator extends App {

  val currentDateStamp = new SimpleDateFormat("yyyyMMdd").format(new Date)

  def generateOutputPath(): String = s"/tmp/deepcortex/e2e-jobs/$currentDateStamp/${UUID.randomUUID}/params.dat"

  def logSuccess(messageType: String, outputPath: String) = {
    println(s"$messageType message was saved to $outputPath")
  }

  def generateDefaults(): Unit = {
    val conf = ConfigFactory.load("default.conf")
    val messageGenerator = new MessageGenerator(conf)

    val cvTrainOutputPath = generateOutputPath()
    messageGenerator.generateCVTrain(cvTrainOutputPath)
    logSuccess("CV train", cvTrainOutputPath)

    val cvPredictOutputPath = generateOutputPath()
    messageGenerator.generateCVPredict(cvPredictOutputPath)
    logSuccess("CV predict", cvTrainOutputPath)

    val cvEvaluateOutputPath = generateOutputPath()
    messageGenerator.generateCVEvaluate(cvEvaluateOutputPath)
    logSuccess("CV evaluate", cvTrainOutputPath)
  }

  generateDefaults()

}
