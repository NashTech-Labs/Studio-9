package cortex.jobmaster.jobs.job

import java.util.UUID

import cortex.io.S3Client
import cortex.jobmaster.jobs.job.image_uploading.ImageFilesSource.S3FilesSequence
import cortex.jobmaster.jobs.job.image_uploading.ImageUploadingJob.ImageUploadingJobParams
import cortex.jobmaster.jobs.job.image_uploading._
import cortex.jobmaster.modules.SettingsModule
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.image_uploading.ImageUploadingModule
import cortex.task.image_uploading.ImageUploadingParams.ProcessingType.{ IMG, RMI, SAR }
import cortex.task.image_uploading.ImageUploadingParams.{ LabeledImageRequest, S3ImportTaskParams }
import cortex.testkit.{ FutureTestUtils, WithLogging }
import org.mockito.Matchers.any
import org.scalamock.scalatest.MockFactory
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global

class ImageUploadingJobInternalTest extends FlatSpec
  with MockFactory
  with FutureTestUtils
  with WithLogging
  with SettingsModule {
  val imageUploadingModule = new ImageUploadingModule
  val imageUploadingJob = new ImageUploadingJob(
    scheduler            = any[TaskScheduler],
    imageUploadingModule = imageUploadingModule,
    outputS3AccessParams = any[S3AccessParams],
    imageUploadingConfig = imageUploadingConfig.copy(
      maxTaskMemSize = 3.0
    )
  )
  val oneMbInBytes = 1024L * 1024L

  "find group size" should "calculate proper group size" in {
    imageUploadingJob.findGroupSize(2) shouldBe 2
    imageUploadingJob.findGroupSize(30) shouldBe 3
    imageUploadingJob.findGroupSize(15) shouldBe 3
    imageUploadingJob.findGroupSize(45) shouldBe 4
    imageUploadingJob.findGroupSize(75) shouldBe 7
    imageUploadingJob.findGroupSize(100) shouldBe 10
    imageUploadingJob.findGroupSize(50000) shouldBe 5000
  }

  "group by type" should "group all tasks by their type" in {
    val res = imageUploadingJob.groupByType(
      baseRelativePath = Some("base/path"),
      imageFiles       = Seq(
        ImageFile("file1.jpg", any[Long], None),
        ImageFile("path2/file1.jpg", any[Long], None),
        ImageFile("file2.jpeg", any[Long], None),
        ImageFile("file3.png", any[Long], None),
        ImageFile("file2", any[Long], None),
        ImageFile("file3", any[Long], None),
        ImageFile("file2.npy", any[Long], None),
        ImageFile("file2.csv", any[Long], None),
        ImageFile("file6.npy", any[Long], None),
        ImageFile("path1/path2/file7.npy", any[Long], None),
        ImageFile("path1/path2/file7.csv", any[Long], None)
      ),
      labels           = Seq(
        ("file1.jpg", "1"),
        ("file2.jpeg", "2"),
        ("file3.png", "3"),
        ("file2", "4"),
        ("file3", "5"),
        ("path2/file1.jpg", "6")
      )
    )
    res(SAR).map(_._2).map(x => (x.imagePath, x.defaultLabels)) shouldBe Seq(
      ("base/path/file2", Seq("4")),
      ("base/path/file3", Seq("5"))
    )
    res(IMG).map(_._2).map(x => (x.imagePath, x.defaultLabels)) shouldBe Seq(
      ("base/path/file1.jpg", Seq("1")),
      ("base/path/path2/file1.jpg", Seq("6")),
      ("base/path/file2.jpeg", Seq("2")),
      ("base/path/file3.png", Seq("3"))
    )
    res(RMI).map(_._2).map(x => (x.imagePath, x.metaPath)) shouldBe Seq(
      ("base/path/file2.npy", Some("base/path/file2.csv")),
      ("base/path/file6.npy", None),
      ("base/path/path1/path2/file7.npy", Some("base/path/path1/path2/file7.csv"))
    )
  }

  "create tasks" should "create tasks with properly distirubuted input images over them" in {
    val sarImages = (0 until 52387).map(_ => ImageFile("path/file123", any[Long], None)).toList
    val pngImages = (0 until 12311).map(_ => ImageFile("path/file123.png", any[Long], None)).toList
    val tasks = imageUploadingJob.createTasks(
      UUID.randomUUID().toString,
      "path",
      Some("base/path"),
      any[S3AccessParams],
      sarImages ++ pngImages,
      Seq(),
      any[Boolean]
    )
    val tasksImages = tasks.map(_.getParams.asInstanceOf[S3ImportTaskParams].images)
    tasksImages.size shouldBe 11
    //guarantee that each task has only one processing type
    tasksImages.map(_.distinct).forall(_.size == 1) shouldBe true
  }

  "splitIfExceedMaxTaskMbSize" should "split groups to more groups if some of them exceed maximal memory limit for task[1]" in {
    val initGroup = Seq(
      oneMbInBytes -> any[LabeledImageRequest],
      oneMbInBytes -> any[LabeledImageRequest],
      oneMbInBytes -> any[LabeledImageRequest],
      oneMbInBytes -> any[LabeledImageRequest],
      oneMbInBytes -> any[LabeledImageRequest],
      oneMbInBytes -> any[LabeledImageRequest],
      oneMbInBytes -> any[LabeledImageRequest]
    )
    val newGroups = imageUploadingJob.splitIfExceedMaxTaskMbSize(initGroup)
    newGroups.size shouldBe 3
    newGroups.forall(_.size <= 3) shouldBe true
  }

  "splitIfExceedMaxTaskMbSize" should "split groups to more groups if some of them exceed maximal memory limit for task[2]" in {
    val initGroup = Seq()
    val newGroups = imageUploadingJob.splitIfExceedMaxTaskMbSize(initGroup)
    newGroups.size shouldBe 0
  }

  "splitIfExceedMaxTaskMbSize" should "split groups to more groups if some of them exceed maximal memory limit for task[3]" in {
    val initGroup = Seq(
      oneMbInBytes -> any[LabeledImageRequest]
    )
    val newGroups = imageUploadingJob.splitIfExceedMaxTaskMbSize(initGroup)
    newGroups.size shouldBe 1
  }

  "splitIfExceedMaxTaskMbSize" should "split groups to more groups if some of them exceed maximal memory limit for task[4]" in {
    val initGroup = Seq(
      oneMbInBytes -> any[LabeledImageRequest],
      oneMbInBytes -> any[LabeledImageRequest],
      oneMbInBytes -> any[LabeledImageRequest],
      oneMbInBytes -> any[LabeledImageRequest]
    )
    val newGroups = imageUploadingJob.splitIfExceedMaxTaskMbSize(initGroup)
    newGroups.size shouldBe 2
    newGroups.forall(_.size == 2) shouldBe true
  }

  "get labels" should "parse csv content and return pairs img_name->label" in {
    val csv = "\n\nlabel1,value1\nlabel2,value2\n\nlabel3,value3\nlabel4,value4\n\n\n".getBytes
    val stubS3Client = stub[S3Client]
    ((a: String, b: String) => stubS3Client.get(a, b)).when(*, *).returns(csv)
    val imageUploadingJob = new ImageUploadingJob(
      scheduler            = any[TaskScheduler],
      imageUploadingModule = any[ImageUploadingModule],
      outputS3AccessParams = any[S3AccessParams],
      imageUploadingConfig = imageUploadingConfig
    ) {
      override protected def getS3Client(s3AccessCredentials: S3AccessParams): S3Client = {
        stubS3Client
      }
    }
    val params = ImageUploadingJobParams(
      albumPath               = any[String],
      imageFilesSource        = any[FileSource[ImageFile]],
      inS3AccessParams        = S3AccessParams(any[String], any[String], any[String], any[String]),
      csvFileS3Path           = Some(any[String]),
      csvFileBytes            = None,
      applyLogTransformations = any[Boolean]
    )
    val res1 = imageUploadingJob.getLabels(params)
    res1 shouldBe Seq(
      ("label1", "value1"),
      ("label2", "value2"),
      ("label3", "value3"),
      ("label4", "value4")
    )

    val params2 = params.copy(
      csvFileS3Path = None,
      csvFileBytes  = Some(csv)
    )
    val res2 = imageUploadingJob.getLabels(params2)
    res2 shouldBe res1
  }

  "upload images" should "not fail main thread in case of internal failures" in {
    val imageUploadingJob = new ImageUploadingJob(
      scheduler            = any[TaskScheduler],
      imageUploadingModule = any[ImageUploadingModule],
      outputS3AccessParams = any[S3AccessParams],
      imageUploadingConfig = imageUploadingConfig
    ) {
      override protected def getS3Client(s3AccessCredentials: S3AccessParams): S3Client = {
        throw new RuntimeException()
      }
    }
    val res = imageUploadingJob.uploadImages(
      jobId  = any[String],
      params = ImageUploadingJobParams(
        albumPath               = any[String],
        imageFilesSource        = any[FileSource[ImageFile]],
        inS3AccessParams        = any[S3AccessParams],
        csvFileS3Path           = any[Option[String]],
        csvFileBytes            = any[Option[Array[Byte]]],
        applyLogTransformations = any[Boolean]
      )
    )
    intercept[RuntimeException](res.await())
  }

  "prepare image files" should "prepare image files leaving only 'well-sized'" in {
    val imageUploadingJob = new ImageUploadingJob(
      scheduler            = any[TaskScheduler],
      imageUploadingModule = any[ImageUploadingModule],
      imageUploadingConfig = imageUploadingConfig,
      outputS3AccessParams = any[S3AccessParams]
    )
    val imageFiles = Seq(
      ImageFile("filename1", 0, None),
      ImageFile("filename2", 11 * 1024, None),
      ImageFile("filename3", 11 * 1024 * 1024, None)
    )
    val (succeed, filtered) = imageUploadingJob.prepareImageFiles(S3FilesSequence(imageFiles))
    succeed.map(_.filename) shouldBe Seq("filename2")
    filtered.map(_.path) shouldBe Seq("filename1", "filename3")
  }
}
