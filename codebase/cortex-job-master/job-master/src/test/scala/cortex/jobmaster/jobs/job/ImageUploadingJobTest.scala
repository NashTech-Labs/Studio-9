package cortex.jobmaster.jobs.job

import java.io.File
import java.util.UUID

import cortex.jobmaster.jobs.job.image_uploading.ImageFilesSource.{ S3FilesSequence, S3FilesSource }
import cortex.jobmaster.jobs.job.image_uploading.ImageUploadingJob.ImageUploadingJobParams
import cortex.jobmaster.jobs.job.image_uploading.{ ImageFile, ImageUploadingJob }
import cortex.jobmaster.modules.SettingsModule
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.image_uploading.ImageUploadingModule
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global

class ImageUploadingJobTest extends FlatSpec
  with FutureTestUtils
  with WithS3AndLocalScheduler
  with SettingsModule {

  val imageUploadingModule = new ImageUploadingModule
  val cpus = 1.0
  val s3AccessParams = S3AccessParams(
    bucket      = baseBucket,
    accessKey   = accessKey,
    secretKey   = secretKey,
    region      = "",
    endpointUrl = Some(fakeS3Endpoint)
  )
  val baseSarPath = "cortex-job-master/e2e/sar_sample"
  val baseJpgPath = "cortex-job-master/e2e/img_sample"
  val baseRmiPath = "cortex-job-master/e2e/rmi_sample"

  override def beforeAll(): Unit = {
    super.beforeAll()

    //upload sar images into fake s3
    new File("../test_data/sar_sample").listFiles().filter(_.isFile).toList.foreach { f =>
      TestUtils.copyToS3(fakeS3Client, baseBucket, s"$baseSarPath/${f.getName}", f.getAbsolutePath)
    }

    //upload jpg images into fake s3
    new File("../test_data/img_sample").listFiles().filter(_.isFile).toList.foreach { f =>
      TestUtils.copyToS3(fakeS3Client, baseBucket, s"$baseJpgPath/${f.getName}", f.getAbsolutePath)
    }

    //upload rmi images into fake s3
    new File("../test_data/rmi_sample").listFiles().filter(_.isFile).toList.foreach { f =>
      TestUtils.copyToS3(fakeS3Client, baseBucket, s"$baseRmiPath/${f.getName}", f.getAbsolutePath)
    }
  }

  lazy val imageUploadingJob = new ImageUploadingJob(
    scheduler            = taskScheduler,
    imageUploadingModule = imageUploadingModule,
    imageUploadingConfig = imageUploadingConfig,
    outputS3AccessParams = s3AccessParams
  )

  "ImageUploadingJob" should "upload SAR images" in {
    val s3FilesSource = S3FilesSource(s3AccessParams, Some(baseSarPath))
    val params = ImageUploadingJobParams(
      albumPath               = s"cortex-job-master/e2e/test_albums/test_album-${UUID.randomUUID().toString}",
      imageFilesSource        = s3FilesSource,
      inS3AccessParams        = s3AccessParams,
      csvFileS3Path           = None,
      csvFileBytes            = None,
      applyLogTransformations = true
    )
    val (result, _) = imageUploadingJob.uploadImages(UUID.randomUUID().toString, params).await()

    result.succeed.size shouldBe 10
    result.succeed.flatMap(_.labels).size shouldBe 10

    //TODO uncomment this condition when the system will be able to maintain metadata
    /*result.succeed.map(_.meta.nonEmpty).forall(_ == true) shouldBe true*/

    result.failed.size shouldBe 2
  }

  "ImageUploadingJob" should "upload IMG sequence" in {
    val s3FilesSource = S3FilesSource(s3AccessParams, Some(baseJpgPath))
    val params = ImageUploadingJobParams(
      albumPath               = s"cortex-job-master/e2e/test_albums/test_album-${UUID.randomUUID().toString}",
      imageFilesSource        = s3FilesSource,
      inS3AccessParams        = s3AccessParams,
      csvFileS3Path           = Some("cortex-job-master/e2e/img_sample/meta.csv"),
      csvFileBytes            = None,
      applyLogTransformations = true
    )
    val (result, _) = imageUploadingJob.uploadImages(UUID.randomUUID().toString, params).await()

    //see cortex-job-master/e2e/img_sample/meta.csv which contains two labels
    result.succeed.flatMap(_.labels).size shouldBe 2
    result.succeed.size shouldBe 13

    //image meta should be empty
    result.succeed.map(_.meta.isEmpty).forall(_ == true) shouldBe true

    //csv files are filtered see [[ImageUploadingJob.imageFilter]]
    result.failed.size shouldBe 2
  }

  "ImageUploadingJob" should "upload RMI sequence" in {
    val s3FilesSource = S3FilesSource(s3AccessParams, Some(baseRmiPath))
    val params = ImageUploadingJobParams(
      albumPath               = s"cortex-job-master/e2e/test_albums/test_album-${UUID.randomUUID().toString}",
      imageFilesSource        = s3FilesSource,
      inS3AccessParams        = s3AccessParams,
      csvFileS3Path           = None,
      csvFileBytes            = None,
      applyLogTransformations = true
    )
    val (result, _) = imageUploadingJob.uploadImages(UUID.randomUUID().toString, params).await()

    result.succeed.size shouldBe 5

    //TODO uncomment this condition when the system will be able to maintain metadata
    /*//the last one doesn't contain meta file [see rmi_sample/example_chip.npy]
    result.succeed.map(_.meta.nonEmpty).shouldBe(Seq(true, true, true, true, false))*/

    result.failed.size shouldBe 1
  }

  "ImageUploadingJob" should "upload only specific images when imageFiles is given" in {
    val s3FilesSource = S3FilesSequence(Seq(
      ImageFile("bmp2_tank_1.jpg", 1L, None),
      ImageFile("bmp2_tank_10.png", 1L, None)
    ), Some(baseJpgPath))
    val params = ImageUploadingJobParams(
      albumPath               = s"cortex-job-master/e2e/test_albums/test_album-${UUID.randomUUID().toString}",
      imageFilesSource        = s3FilesSource,
      inS3AccessParams        = s3AccessParams,
      csvFileS3Path           = None,
      csvFileBytes            = None,
      applyLogTransformations = true
    )
    val (result, _) = imageUploadingJob.uploadImages(UUID.randomUUID().toString, params).await()

    //see cortex-job-master/e2e/img_sample/meta.csv which contains two labels
    result.succeed.size shouldBe 2

    //image meta should be empty
    result.succeed.map(_.meta.isEmpty).forall(_ == true) shouldBe true

    // no files should fail
    result.failed.size shouldBe 0
  }

  "ImageUploadingJob" should "keep referenceId when imageFiles is given" in {
    val s3FilesSource = S3FilesSequence(Seq(
      ImageFile("bmp2_tank_1.jpg", 1L, Some("i1")),
      ImageFile("bmp2_tank_10.png", 1L, Some("i2"))
    ), Some(baseJpgPath))
    val params = ImageUploadingJobParams(
      albumPath               = s"cortex-job-master/e2e/test_albums/test_album-${UUID.randomUUID().toString}",
      imageFilesSource        = s3FilesSource,
      inS3AccessParams        = s3AccessParams,
      csvFileS3Path           = None,
      csvFileBytes            = None,
      applyLogTransformations = true
    )
    val (result, _) = imageUploadingJob.uploadImages(UUID.randomUUID().toString, params).await()

    //see cortex-job-master/e2e/img_sample/meta.csv which contains two labels
    result.succeed.size shouldBe 2

    //all references should be defined
    result.succeed.forall(_.referenceId.isDefined) shouldBe true

    //references should match
    result.succeed.find(_.referenceId.contains("i1")).fold("")(_.name) shouldBe "bmp2_tank_1.jpg"
    result.succeed.find(_.referenceId.contains("i2")).fold("")(_.name) shouldBe "bmp2_tank_10.png"

    //image meta should be empty
    result.succeed.map(_.meta.isEmpty).forall(_ == true) shouldBe true

    // no files should fail
    result.failed.size shouldBe 0
  }
}
