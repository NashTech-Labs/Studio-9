package cortex.jobmaster.jobs.job

import cortex.jobmaster.jobs.job.splitter.SplitterJob
import cortex.jobmaster.modules.SettingsModule
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.transform.splitter.SplitterModule
import cortex.task.transform.splitter.SplitterParams.SplitterTaskParams
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global

class SplitterJobTest extends FlatSpec
  with FutureTestUtils
  with WithS3AndLocalScheduler
  with SettingsModule {

  "Split job" should "split input file" in {
    val inputDsPath = s"some/path/train.csv"
    TestUtils.copyToS3(fakeS3Client, baseBucket, inputDsPath, "../test_data/mvh_pipeline_combined.csv")

    val basePath = "some/base/path"
    val splitterModule = new SplitterModule(5, "output-", basePath)
    val transformJob = new SplitterJob(taskScheduler, splitterModule, splitterConfig)
    val jobId = java.util.UUID.randomUUID.toString

    val s3AccessParams = S3AccessParams(
      bucket      = baseBucket,
      accessKey   = accessKey,
      secretKey   = secretKey,
      region      = "some_region",
      endpointUrl = Some(fakeS3Endpoint)
    )
    val splitterTaskParams = SplitterTaskParams(
      inputPaths          = Seq(inputDsPath),
      storageAccessParams = s3AccessParams
    )
    val splitInputResult = transformJob.splitInput(
      jobId              = jobId,
      splitterTaskParams = splitterTaskParams
    )

    val result = splitInputResult.await()

    val outputPaths = Seq(
      s"$basePath/$jobId/splitter/output-0.csv",
      s"$basePath/$jobId/splitter/output-1.csv",
      s"$basePath/$jobId/splitter/output-2.csv",
      s"$basePath/$jobId/splitter/output-3.csv",
      s"$basePath/$jobId/splitter/output-4.csv"
    )
    result.parts shouldBe outputPaths

    val lines = outputPaths
      .map(fakeS3Client.get(baseBucket, _))
      .map(new String(_))
      .flatMap(_.split("\n"))

    val header = lines.head
    lines.count(_ == header) shouldBe 5
    lines.count(_ != header) shouldBe 56
  }
}
