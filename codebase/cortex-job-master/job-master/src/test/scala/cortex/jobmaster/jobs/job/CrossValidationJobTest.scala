package cortex.jobmaster.jobs.job

import java.util.UUID

import cortex.jobmaster.jobs.job.cross_validation.CrossValidationJob
import cortex.jobmaster.jobs.job.cross_validation.CrossValidationJob.CrossValidationParams
import cortex.jobmaster.jobs.job.splitter.SplitterJob
import cortex.jobmaster.modules.SettingsModule
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.tabular_data.mvh.MVHModule
import cortex.task.tabular_data.mvh.MVHParams.MVHTrainParams
import cortex.task.tabular_data.tabularpipeline.TabularPipelineModule
import cortex.task.tabular_data.{ AllowedModelPrimitive, AllowedTaskType }
import cortex.task.transform.splitter.SplitterModule
import cortex.task.transform.splitter.SplitterParams.SplitterTaskParams
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global

class CrossValidationJobTest extends FlatSpec
  with FutureTestUtils
  with WithS3AndLocalScheduler
  with SettingsModule {

  "CrossValidationJob" should "calculate cv score" in {
    val dataPath = s"some/path/train.csv"
    val baseOutputPath = "some/path"
    val modelsBasePath = "tabular/models"
    TestUtils.copyToS3(fakeS3Client, baseBucket, dataPath, "../test_data/train_pipeline.csv")

    val s3AccessParams = S3AccessParams(
      bucket      = baseBucket,
      accessKey   = accessKey,
      secretKey   = secretKey,
      region      = "",
      endpointUrl = Some(fakeS3Endpoint)
    )

    val mvhModule = new MVHModule(modelsBasePath)
    val splitter = new SplitterModule(5, "output-", baseOutputPath)
    val splitterJob = new SplitterJob(taskScheduler, splitter, splitterConfig)
    val tabularPipelineModule = new TabularPipelineModule(baseOutputPath)
    val cvJob = new CrossValidationJob(taskScheduler, tabularPipelineModule, s3AccessParams, crossValidationConfig)
    val jobId = UUID.randomUUID().toString

    val mvhTrainParams = MVHTrainParams(
      trainInputPaths       = Seq(dataPath),
      numericalPredictors   = Seq("second", "fourth"),
      categoricalPredictors = Seq("first", "third"),
      storageAccessParams   = s3AccessParams,
      modelsBasePath        = modelsBasePath
    )
    val mvhTrainTaskId = UUID.randomUUID().toString
    val mvhTrainTask = mvhModule.trainTask(
      id       = mvhTrainTaskId,
      jobId    = mvhTrainTaskId,
      taskPath = mvhTrainTaskId,
      params   = mvhTrainParams,
      cpus     = 1.0,
      memory   = 512
    )

    val cvScore = for {
      mvhTrainResult <- taskScheduler.submitTask(mvhTrainTask)
      splitterResult <- {
        val params = SplitterTaskParams(Seq(dataPath), s3AccessParams)
        splitterJob.splitInput(jobId, params)
      }
      cvScore <- {
        val cvParams = CrossValidationParams(
          taskType              = AllowedTaskType.Regressor,
          modelPrimitive        = AllowedModelPrimitive.Linear,
          weightsCol            = None,
          response              = "ycol",
          numericalPredictors   = Seq("second", "fourth"),
          categoricalPredictors = Seq("first", "third"),
          hyperparamsDict       = Map(),
          mvhModelId            = mvhTrainResult.modelReference.id,
          modelsBasePath        = modelsBasePath
        )
        cvJob.getCVScore(jobId, splitterResult, cvParams)
      }
    } yield cvScore

    val (score, _) = cvScore.await()
    score should be > 0.0
  }
}
