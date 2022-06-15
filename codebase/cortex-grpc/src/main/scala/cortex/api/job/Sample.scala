package cortex.api.job

import com.trueaccord.scalapb.GeneratedMessage
import cortex.api.job.album.common.{ Image, Tag, TaggedImage }
import cortex.api.job.computervision.{ CVModelTrainRequest, TLModelType }
import cortex.api.job.common.ClassReference

/**
  * General job request serialization sample.
  * Refer to this as a documentation of how to build job request message and read its data.
  */

// scalastyle:off
object Sample extends App {

  println("Building job request")
  val jobRequest = buildJob

  println("=======================================")

  println("Reading job request")
  processJobRequest(jobRequest)

  /**
    * Builds, prints and returns sample job request.
    */
  private def buildJob: JobRequest = {
    val images = Seq(
      TaggedImage(Some(Image("https://deepcortex.ai/pic1.png")), Seq(Tag("label1"))),
      TaggedImage(Some(Image("https://deepcortex.ai/pic2.png")), Seq(Tag("label1"))),
      TaggedImage(Some(Image("https://deepcortex.ai/pic3.png")), Seq(Tag("label2"))),
      TaggedImage(Some(Image("https://deepcortex.ai/pic4.png")), Seq(Tag("label2")))
    )

    val modelType = TLModelType(TLModelType.Type.ClassifierType(ClassReference(
      packageLocation = Some("/ml_lib/classifiers/vgg16"),
      moduleName = "classifier",
      className = "vgg16"
    )))

    val featureExtractorClassReference = ClassReference(
      packageLocation = Some("/ml_lib/feature_extractor/scae"),
      moduleName = "feature_extractor",
      className = "scae"
    )

    val nestedRequest = CVModelTrainRequest(
      featureExtractorId = Some("wiejt"),
      featureExtractorClassReference = Some(featureExtractorClassReference),
      images = images,
      filePathPrefix = "5hg8aw",
      modelType = Some(modelType),
      augmentationParams = None
    )

    val jobRequest = JobRequest(
      `type` = JobType.CVModelTrain,
      payload = nestedRequest.toByteString
    )

    println(s"Built job request: $jobRequest")
    println(s"Nested request: $nestedRequest")

    jobRequest
  }

  /**
    * Given a job request, reads and prints its content.
    * @param job Request to read.
    */
  private def processJobRequest(job: JobRequest): Unit = {
    import JobType._

    val deserializeAction: (Array[Byte]) => GeneratedMessage = job.`type` match {
      case CVModelTrain => computervision.CVModelTrainRequest.parseFrom
      case CVEvaluate => computervision.EvaluateRequest.parseFrom
      case CVPredict => computervision.PredictRequest.parseFrom
      case TabularPredict => tabular.PredictRequest.parseFrom
      case TabularTrain => tabular.TrainRequest.parseFrom
      case TabularEvaluate => tabular.EvaluateRequest.parseFrom
      case S3ImagesImport => album.uploading.S3ImagesImportRequest.parseFrom
      case S3VideoImport => album.uploading.S3VideoImportRequest.parseFrom
      case OnlinePrediction => online.prediction.PredictRequest.parseFrom
      case TabularUpload => table.TableUploadRequest.parseFrom
      case CVModelImport => computervision.CVModelImportRequest.parseFrom
      case AlbumAugmentation => album.augmentation.AugmentationRequest.parseFrom
      case TabularColumnStatistics => table.TabularColumnStatisticsRequest.parseFrom
      case ProjectPackage => project.`package`.ProjectPackageRequest.parseFrom
      case TabularModelImport => tabular.TabularModelImportRequest.parseFrom
      case Pipeline => pipeline.PipelineRunRequest.parseFrom
      case S3DatasetImport => dataset.S3DatasetImportRequest.parseFrom
      case S3DatasetExport => dataset.S3DatasetExportRequest.parseFrom
      case Unrecognized(_) => throw new Exception("Unrecognized job type value")
    }

    val requestData = deserializeAction(job.payload.toByteArray)

    println(s"Read job request $job:")
    println(s"The request data class is ${requestData.getClass.getSimpleName}")
    println(s"The request data is $requestData")
  }

}
