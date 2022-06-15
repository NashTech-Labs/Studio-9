package cortex.jobmaster.jobs.job

import java.util.UUID

import cortex.jobmaster.jobs.job.cross_validation.CrossValidationJob
import cortex.jobmaster.jobs.job.splitter.SplitterJob
import cortex.jobmaster.jobs.job.tabular.TabularJob
import cortex.jobmaster.jobs.job.tabular.TabularJob.{
  TabularJobEvaluateParams,
  TabularJobPredictParams,
  TabularJobTrainPredictParams
}
import cortex.jobmaster.jobs.tuning.{ HyperParamRandomSearch, HyperParamSelector }
import cortex.jobmaster.modules.SettingsModule
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.common.ClassReference
import cortex.task.tabular_data.AllowedTaskType
import cortex.task.tabular_data.mvh.MVHModule
import cortex.task.tabular_data.tabularpipeline.TabularPipelineModule
import cortex.task.transform.splitter.SplitterModule
import cortex.testkit.OptionHelpers._
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global

class TabularJobTest extends FlatSpec
  with FutureTestUtils
  with WithS3AndLocalScheduler
  with SettingsModule {
  lazy val s3AccessParams = S3AccessParams(
    bucket      = baseBucket,
    accessKey   = accessKey,
    secretKey   = secretKey,
    region      = "",
    endpointUrl = Some(fakeS3Endpoint)
  )
  val modelsBasePath = "tabular/models"
  var trainDsPath: String = _
  var evaluateDsPath: String = _
  var columnsMapper: Map[String, String] = _
  var modelId: String = _
  var tabularTrainPredictJob: TabularJob = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    trainDsPath = "some/path/mvh_pipeline_combined.csv"
    evaluateDsPath = "some/path/mvh_pipeline_combined_predict.csv"

    //prepare train data
    val localDataPath = "../test_data/mvh_pipeline_combined.csv"
    TestUtils.copyToS3(fakeS3Client, baseBucket, trainDsPath, localDataPath)

    //prepare predict data
    val data = new String(TestUtils.getBytesFromS3(fakeS3Client, baseBucket, localDataPath)).split("\n")
    val oldHeader = data.head.split(",")
    //modify header to test mapping feature
    val modifiedHeader = oldHeader.map(x => s""" "${x.filter(_ != '"')}_new" """.trim)
    columnsMapper = modifiedHeader.zip(oldHeader).map { case (x, y) => (x.replace("\"", ""), y.replace("\"", "")) }.toMap[String, String]
    val modifiedData = modifiedHeader.mkString(",") + "\n" + data.tail.mkString("\n")
    fakeS3Client.put(baseBucket, evaluateDsPath, modifiedData.getBytes())

    val splitterModule = new SplitterModule(5, "output-", "splitter/output/path")
    val splitterJob = new SplitterJob(taskScheduler, splitterModule, splitterConfig)
    val tabularModule = new TabularPipelineModule("tabular/output/path")
    val crossValidationJob = new CrossValidationJob(taskScheduler, tabularModule, s3AccessParams, crossValidationConfig)
    val hpRandomSearch = new HyperParamRandomSearch() {
      override def getHyperParams(taskType: AllowedTaskType, numSample: Int): Seq[HyperParamRandomSearch.ModelWithHyperParams] = {
        super.getHyperParams(taskType, numSample).take(1) //for decreasing time of cross validation
      }
    }
    val mVHModule = new MVHModule("mvh/output/path")
    val hpSelector = new HyperParamSelector(crossValidationJob, hpRandomSearch)
    tabularTrainPredictJob = new TabularJob(
      scheduler             = taskScheduler,
      hyperParamSelector    = hpSelector,
      splitterJob           = splitterJob,
      mvhModule             = mVHModule,
      tabularPipelineModule = tabularModule,
      storageAccessParams   = s3AccessParams,
      tabularJobConfig      = tabularConfig,
      modelsBasePath        = modelsBasePath,
      tmpDirPath            = tmpDirPath
    )
  }

  "Tabular train predict job" should "trainPredict [regressor case]" in {
    val jobId = UUID.randomUUID().toString
    val (trainPredictResults, _) = {
      val params = TabularJobTrainPredictParams(
        trainInputPaths       = Seq(trainDsPath),
        taskType              = AllowedTaskType.Regressor,
        numericalPredictors   = Seq("second", "fourth"),
        categoricalPredictors = Seq("first", "third"),
        weightsCol            = None,
        response              = "ycol",
        predictColName        = "predictCol",
        probabilityColPrefix  = None
      )
      tabularTrainPredictJob.trainPredict(jobId, params)
    }.await()

    modelId = trainPredictResults.modelReference.id
    val data = new String(fakeS3Client.get(baseBucket, trainPredictResults.predictPath)).split("\n")
    val header = data.headOption.getMandatory
    val expected = Set("first", "second", "third", "fourth", "ycol", "predictCol")
    header.split(",").toSet shouldBe expected
    trainPredictResults.headerColNames.toSet shouldBe expected
    trainPredictResults.probabilityColNames shouldBe Map.empty[String, String]
  }

  it should "evaluate [regressor case]" in {
    val jobId = UUID.randomUUID().toString
    val (evaluateResults, _) = {
      val params = TabularJobEvaluateParams(
        inputDataPaths      = Seq(evaluateDsPath),
        weightsCol          = None,
        modelId             = modelId,
        columnsMapping      = columnsMapper,
        predictColName      = "predictCol",
        probabilityColNames = Map(),
        classReference      = ClassReference(None, "ml_lib.tabular.fused_pipeline_stage", "FusedTabularPipeline")
      )
      tabularTrainPredictJob.evaluate(jobId, params)
    }.await()

    val data = new String(fakeS3Client.get(baseBucket, evaluateResults.predictPath)).split("\n")
    val header = data.headOption.getMandatory
    evaluateResults.scoreOutput > 0 shouldBe true
    val expected = Set("first_new", "second_new", "third_new", "fourth_new", "ycol_new", "predictCol")
    header.split(",").toSet shouldBe expected
    evaluateResults.headerColNames.toSet shouldBe expected
  }

  it should "predict [regressor case]" in {
    val jobId = UUID.randomUUID().toString
    val (predictResults, _) = {
      val params = TabularJobPredictParams(
        inputDataPaths      = Seq(evaluateDsPath),
        modelId             = modelId,
        columnsMapping      = columnsMapper,
        predictColName      = "predictCol",
        probabilityColNames = Map(),
        classReference      = ClassReference(None, "ml_lib.tabular.fused_pipeline_stage", "FusedTabularPipeline")
      )
      tabularTrainPredictJob.predict(jobId, params)
    }.await()

    val data = new String(fakeS3Client.get(baseBucket, predictResults.predictPath)).split("\n")
    val header = data.headOption.getMandatory
    val expected = Set("first_new", "second_new", "third_new", "fourth_new", "ycol_new", "predictCol")
    header.split(",").toSet shouldBe expected
    predictResults.headerColNames.toSet shouldBe expected
  }
}
