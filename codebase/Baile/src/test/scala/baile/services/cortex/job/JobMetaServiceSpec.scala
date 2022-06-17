package baile.services.cortex.job

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.{ Date, UUID }

import baile.BaseSpec
import baile.services.remotestorage.{ File, S3StorageService }
import cortex.api.job.album.common.{ Image, Tag, TaggedImage }
import cortex.api.job.{ JobRequest, JobType }
import cortex.api.job.common.ClassReference
import cortex.api.job.computervision._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._

import scala.concurrent.ExecutionContext

class JobMetaServiceSpec extends BaseSpec {

  private val remoteStorage = mock[S3StorageService]
  private val jobMetaService = new JobMetaService(conf, remoteStorage)
  private val jobId = UUID.randomUUID

  val fullPath: String = {
    val baseInputPath = conf.getString("cortex.job.dir")
    val currentDateStamp: String = new SimpleDateFormat("yyyyMMdd").format(new Date)
    s"$baseInputPath/$currentDateStamp/$jobId/params.dat"
  }

  val error = new RuntimeException("BOOM")
  val images = Seq(
    TaggedImage(Some(Image("image1")), Seq(Tag("label1"))),
    TaggedImage(Some(Image("image2")), Seq(Tag("label2")))
  )

  "JobMetaService#writeMeta" should {

    val jobRequest = JobRequest(
      `type` = JobType.CVModelTrain,
      payload = CVModelTrainRequest(
        featureExtractorId = Some("feid"),
        featureExtractorClassReference = Some(ClassReference(
          None,
          "ml_lib.feature_extractors.backbones",
          "StackedAutoEncoder"
        )),
        images = Seq(
          TaggedImage(Some(Image("image1")), Seq(Tag("label1"))),
          TaggedImage(Some(Image("image2")), Seq(Tag("label2")))
        ),
        filePathPrefix = randomString()
      ).toByteString
    )

    "successfully write meta to remote storage" in {
      when(remoteStorage.write(any[Array[Byte]], anyString)(any[ExecutionContext]))
        .thenReturn(future(File(randomString(), randomInt(20000), Instant.now)))

      whenReady(jobMetaService.writeMeta(jobId, jobRequest)) { result =>
        result shouldBe fullPath
      }
    }

    "fail to write meta to remote storage" in {
      when(remoteStorage.write(any[Array[Byte]], anyString)(any[ExecutionContext]))
        .thenReturn(future(error))

      whenReady(jobMetaService.writeMeta(jobId, jobRequest).failed) { result =>
        result shouldBe error
      }
    }

  }

  "JobMetaService#readRawMeta" should {

    val jobResult = CVModelTrainResult(
      images = images.map { image =>
        PredictedImage(
          image = image.image,
          predictedTags = image.tags.map { tag =>
            PredictedTag(
              tag = Some(tag),
              confidence = 0.5
            )
          }
        )
      }
    )
    val outputPath = "output/path"

    "successfully read raw meta from remote storage" in {
      val rawResult = jobResult.toByteString.toByteArray

      when(remoteStorage.read(anyString)(any[ExecutionContext]))
        .thenReturn(future(rawResult))
        .thenReturn(future(rawResult))

      whenReady(jobMetaService.readRawMeta(jobId, outputPath)) { result =>
        result shouldBe rawResult
      }
    }

    "fail to write meta to remote storage" in {
      when(remoteStorage.read(anyString)(any[ExecutionContext]))
        .thenReturn(future(error))

      whenReady(jobMetaService.readRawMeta(jobId, outputPath).failed) { result =>
        result shouldBe error
      }
    }

  }

}
