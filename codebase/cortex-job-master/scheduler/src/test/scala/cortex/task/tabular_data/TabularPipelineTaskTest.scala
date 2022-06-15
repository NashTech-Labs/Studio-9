package cortex.task.tabular_data

import java.util.UUID

import cortex.TestUtils
import cortex.task.HyperParam
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.common.ClassReference
import cortex.task.tabular_data.mvh.MVHModule
import cortex.task.tabular_data.mvh.MVHParams.MVHTrainParams
import cortex.task.tabular_data.tabularpipeline.TabularPipelineModule
import cortex.task.tabular_data.tabularpipeline.TabularPipelineParams._
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

/**
 * Testing missing value handler task
 */
class TabularPipelineTaskTest extends FlatSpec with WithS3AndLocalScheduler with FutureTestUtils {

  var dataPath: String = _
  var modelId: String = _
  var mvhModelId: String = _
  val cpus = 1.0
  val memory = 512.0
  val modelsBasePath = "tabular/models"
  val predictPath = "tabular/predict"

  lazy val taskPath = s"$baseBucket/tmp"
  lazy val mvhModule = new MVHModule(taskPath)
  lazy val tabularModule = new TabularPipelineModule(taskPath)
  lazy val s3AccessParams = S3AccessParams(
    bucket      = baseBucket,
    accessKey   = accessKey,
    secretKey   = secretKey,
    region      = "",
    endpointUrl = Some(fakeS3Endpoint)
  )

  override def beforeAll(): Unit = {
    super.beforeAll()
    dataPath = s"some/path/train_pipeline.csv"
    TestUtils.copyToS3(fakeS3Client, baseBucket, dataPath, "../test_data/train_pipeline.csv")

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
      memory   = memory
    )

    val result = taskScheduler.submitTask(tpTask).await()
    mvhModelId = result.modelReference.id
  }

  "TabularPipeline" should "trainPredict" in {
    val taskId = UUID.randomUUID().toString
    val trainPredictParams = TabularTrainPredictParams(
      trainInputPaths       = Seq(dataPath),
      taskType              = AllowedTaskType.Regressor,
      modelPrimitive        = AllowedModelPrimitive.Linear,
      response              = "ycol",
      weightsCol            = None,
      numericalPredictors   = Seq("second", "fourth"),
      categoricalPredictors = Seq("first", "third"),
      predictColName        = "predictionCol",
      probabilityColPrefix  = Some("prefix_"),
      hyperparamsDict       = Map[String, HyperParam](),
      storageAccessParams   = s3AccessParams,
      mvhModelId            = mvhModelId,
      modelsBasePath        = modelsBasePath,
      predictPath           = predictPath
    )
    val result = {
      val tpTask = tabularModule.trainPredictTask(taskId, taskId, taskId, trainPredictParams, cpus = cpus, memory = memory)
      taskScheduler.submitTask(tpTask)
    }.await()
    modelId = result.modelReference.id
    result.modelReference.path should startWith(modelsBasePath)
    result.predictPath shouldBe s"$predictPath/FusedPipelineStage_prediction"
  }

  it should "evaluate" in {
    val taskId = UUID.randomUUID().toString
    val evaluateResult = {
      val params = TabularEvaluateParams(
        validateInputPaths  = Seq(dataPath),
        modelId             = modelId,
        weightsCol          = None,
        columnsMapping      = Map(),
        predictColName      = "prediction_result",
        probabilityColNames = List(),
        storageAccessParams = s3AccessParams,
        classReference      = ClassReference(None, "ml_lib.tabular.fused_pipeline_stage", "FusedTabularPipeline"),
        modelsBasePath      = modelsBasePath,
        predictPath         = predictPath
      )
      val task = tabularModule.evaluateTask(taskId, taskId, taskId, params, cpus = cpus, memory = memory)
      taskScheduler.submitTask(task)
    }.await()
    evaluateResult.predictPath shouldBe s"$predictPath/FusedPipelineStage_prediction"
  }

  it should "score" in {
    val taskId = UUID.randomUUID().toString
    val scoreResult = {
      val params = TabularScoreParams(
        validateInputPaths  = Seq(dataPath),
        modelId             = modelId,
        storageAccessParams = s3AccessParams,
        modelsBasePath      = modelsBasePath
      )
      val task = tabularModule.scoreTask(taskId, taskId, taskId, params, cpus, memory)
      taskScheduler.submitTask(task)
    }.await()
    scoreResult.scoreOutput > 0 shouldBe true
  }

  it should "predict" in {
    val taskId = UUID.randomUUID().toString
    val predictResult = {
      val predictParams = TabularPredictParams(
        validateInputPaths  = Seq(dataPath),
        modelId             = modelId,
        columnsMapping      = Map(),
        storageAccessParams = s3AccessParams,
        predictColName      = "prediction_result",
        probabilityColNames = List(),
        classReference      = ClassReference(None, "ml_lib.tabular.fused_pipeline_stage", "FusedTabularPipeline"),
        modelsBasePath      = modelsBasePath,
        predictPath         = predictPath
      )
      val task = tabularModule.predictTask(
        id       = taskId,
        jobId    = taskId,
        taskPath = taskId,
        params   = predictParams,
        cpus     = cpus,
        memory   = memory
      )
      taskScheduler.submitTask(task)
    }.await()
    predictResult.predictPath shouldBe s"$predictPath/FusedPipelineStage_prediction"
  }
}
