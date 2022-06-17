package cortex.jobmaster.jobs.job

import java.io.File
import java.util.UUID

import cortex.jobmaster.jobs.job.dataset.{ DatasetTransferConfig, DatasetTransferJob, TransferFileSource }
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.dataset.DatasetTransferModule
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global

class DatasetTransferJobTest extends FlatSpec
  with FutureTestUtils
  with WithS3AndLocalScheduler {

  val module = new DatasetTransferModule()
  val s3AccessParams = S3AccessParams(
    bucket      = baseBucket,
    accessKey   = accessKey,
    secretKey   = secretKey,
    region      = "",
    endpointUrl = Some(fakeS3Endpoint)
  )
  val baseFilesPath = "cortex-job-master/test_data"

  override def beforeAll(): Unit = {
    super.beforeAll()

    //upload files into fake s3 with nested dirs
    new File("../test_data").listFiles().filter(_.isFile).toList.foreach { f =>
      TestUtils.copyToS3(fakeS3Client, baseBucket, s"$baseFilesPath/${f.getName}", f.getAbsolutePath)
    }
    new File("../test_data/img_sample").listFiles().filter(_.isFile).toList.foreach { f =>
      TestUtils.copyToS3(fakeS3Client, baseBucket, s"$baseFilesPath/img_sample/${f.getName}", f.getAbsolutePath)
    }
  }

  lazy val datasetTransferJob = new DatasetTransferJob(
    scheduler = taskScheduler,
    module    = module,
    config    = DatasetTransferConfig()
  )

  "DatasetTransferJob" should "upload all files" in {
    val targetPath = s"test_dataset-${UUID.randomUUID().toString}"
    val s3FilesSource = TransferFileSource.S3FileSource(s3AccessParams, Some(baseFilesPath))
    val (result, _) = datasetTransferJob.transferDataset(
      UUID.randomUUID().toString,
      baseFilesPath,
      targetPath,
      s3FilesSource,
      s3AccessParams,
      s3AccessParams
    ).await()

    result.succeed.map(_.path) shouldBe s3FilesSource.getFiles.map(_.filename)
    result.failed.size shouldBe 0
  }
}
