package cortex.jobmaster.orion.service.domain.fixtures

import java.io.File
import java.util.UUID

import cortex.api.job.album.common.{ Image, Tag, TaggedImage }
import cortex.jobmaster.jobs.job.TestUtils
import cortex.jobmaster.jobs.job.image_uploading.ImageFilesSource.S3FilesSource
import cortex.jobmaster.jobs.job.image_uploading.ImageUploadingJob.{ ImageUploadingJobParams, ImageUploadingJobResults, UploadedImage }
import cortex.jobmaster.jobs.job.image_uploading.ImageUploadingJob
import cortex.jobmaster.modules.SettingsModule
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.image_uploading.ImageUploadingModule
import cortex.testkit.WithS3AndLocalScheduler
import org.scalatest.{ BeforeAndAfterAll, Suite }

import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait UploadedImages extends BeforeAndAfterAll { this: Suite with WithS3AndLocalScheduler with SettingsModule =>

  val albumPath = s"test_albums/test_album-${UUID.randomUUID().toString}"
  val outputAlbumPath = s"test_albums/test_output_album-${UUID.randomUUID().toString}"

  private var imageUploadingResult: ImageUploadingJobResults = _

  protected def taggedImages: Seq[TaggedImage] = {
    imageUploadingResult.succeed.map {
      case UploadedImage(_, Seq(label), _, img, _, _) =>
        TaggedImage(
          Some(Image(filePath    = img, referenceId = Some(img), displayName = Some(img))),
          Seq(Tag(label, None))
        )
    }
  }

  protected def unlabeledImages: Seq[Image] = {
    imageUploadingResult.succeed.map {
      case UploadedImage(_, _, _, img, _, _) =>
        Image(
          filePath    = img,
          referenceId = Some(img)
        )
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll() // To be stackable, must call super.beforeAll

    val baseSarPath = "cortex-job-master/e2e/sar_sample"

    val s3AccessParams = S3AccessParams(
      bucket      = baseBucket,
      accessKey   = accessKey,
      secretKey   = secretKey,
      region      = "",
      endpointUrl = Some(fakeS3Endpoint)
    )

    val imageUploadingJob = new ImageUploadingJob(
      scheduler            = taskScheduler,
      imageUploadingModule = new ImageUploadingModule,
      imageUploadingConfig = imageUploadingConfig,
      outputS3AccessParams = s3AccessParams
    )

    //upload sar images to fake s3
    new File("../test_data/sar_sample").listFiles().filter(_.isFile).toList.foreach { f =>
      TestUtils.copyToS3(fakeS3Client, baseBucket, s"$baseSarPath/${f.getName}", f.getAbsolutePath)
    }

    val s3FilesSource = S3FilesSource(s3AccessParams, Some(baseSarPath))
    val params = ImageUploadingJobParams(
      albumPath               = albumPath,
      imageFilesSource        = s3FilesSource,
      inS3AccessParams        = s3AccessParams,
      csvFileS3Path           = None,
      csvFileBytes            = None,
      applyLogTransformations = true
    )

    val (jobResult, _) = Await.result(imageUploadingJob.uploadImages("", params), Duration.Inf)

    imageUploadingResult = jobResult
  }

  override def afterAll(): Unit = {
    super.afterAll() // To be stackable, must call super.afterAll
  }
}
