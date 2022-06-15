package cortex.task.tabular_data

import java.util.UUID

import cortex.TestUtils
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.tabular_data.mvh.MVHModule
import cortex.task.tabular_data.mvh.MVHParams._
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class MVHTrainPredictTaskTest extends FlatSpec with WithS3AndLocalScheduler with FutureTestUtils {

  val dataPath: String = "some/path/train_mvh.csv"
  val cpus = 1.0
  val mem = 64.0
  val modelsBasePath = "tabular/models"
  lazy val taskPath = s"$baseBucket/tmp"
  lazy val mvhModule = new MVHModule(taskPath)
  lazy val s3AccessParams = S3AccessParams(
    bucket      = baseBucket,
    accessKey   = accessKey,
    secretKey   = secretKey,
    region      = "",
    endpointUrl = Some(fakeS3Endpoint)
  )

  override def beforeAll(): Unit = {
    super.beforeAll()
    TestUtils.copyToS3(fakeS3Client, baseBucket, dataPath, "../test_data/mvh_pipeline_combined.csv")
  }

  "MissingValueHandler" should "trainPredict" in {
    val trainParams = MVHTrainParams(
      trainInputPaths       = Seq(dataPath),
      numericalPredictors   = Seq("second", "fourth"),
      categoricalPredictors = Seq("first", "third"),
      storageAccessParams   = s3AccessParams,
      modelsBasePath        = modelsBasePath
    )

    val taskId = UUID.randomUUID().toString
    val tpTask = mvhModule.trainTask(
      id       = taskId,
      jobId    = taskId,
      taskPath = taskId,
      params   = trainParams,
      cpus     = cpus,
      memory   = mem
    )

    val result = taskScheduler.submitTask(tpTask).await()
    result.modelReference.path should startWith(modelsBasePath)
  }
}
