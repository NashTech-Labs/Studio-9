package cortex.jobmaster.orion.service

import java.util.UUID

import cortex.E2EConfig
import cortex.api.job.table._
import cortex.api.job.tabular._
import cortex.io.S3Client
import cortex.jobmaster.jobs.job.cross_validation.{ CrossValidationJob, CrossValidationJobConfig }
import cortex.jobmaster.jobs.job.dremio_exporter.{ DremioExporterJob, DremioExporterJobConfig }
import cortex.jobmaster.jobs.job.dremio_importer.{ DremioImporterJob, DremioImporterJobConfig }
import cortex.jobmaster.jobs.job.splitter.{ SplitterJob, SplitterJobConfig }
import cortex.jobmaster.jobs.job.tabular._
import cortex.jobmaster.jobs.tuning.{ HyperParamRandomSearch, HyperParamSelector }
import cortex.jobmaster.orion.service.domain.TabularDataService
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.tabular_data.mvh.MVHModule
import cortex.task.tabular_data.tabularpipeline.TabularPipelineModule
import cortex.task.tabular_data.{ AllowedTaskType, TabularModelImportModule }
import cortex.task.transform.exporter.dremio.DremioExporterModule
import cortex.task.transform.importer.dremio.DremioImporterModule
import cortex.task.transform.splitter.SplitterModule
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global


class E2ETabularDataServiceTest extends FlatSpec
  with WithS3AndLocalScheduler
  with FutureTestUtils
  with E2EConfig {

  lazy val splitterModule = new SplitterModule(5, "output-", s3TestPath)
  lazy val tabularModule = new TabularPipelineModule(s3TestPath)
  lazy val splitterJob = new SplitterJob(taskScheduler, splitterModule, SplitterJobConfig(1.0, 512.0))

  val s3AccessParams = S3AccessParams(
    bucket      = s3Bucket,
    accessKey   = s3AccessKey,
    secretKey   = s3SecretKey,
    region      = s3Region
  )
  val modelsBasePath = "tabular/models"
  val tmpDirPath = "tmp"
  lazy val crossValidationJob = new CrossValidationJob(taskScheduler, tabularModule, s3AccessParams, CrossValidationJobConfig(1.0, 512.0))
  lazy val hpRandomSearch = new HyperParamRandomSearch() {
    override def getHyperParams(taskType: AllowedTaskType, numSample: Int) = {
      super.getHyperParams(taskType, numSample).take(1) //for decreasing time of cross validation
    }
  }
  lazy val hpSelector = new HyperParamSelector(crossValidationJob, hpRandomSearch)
  lazy val dremioImporterJob = new DremioImporterJob(
    taskScheduler,
    new DremioImporterModule,
    dremioAccessParams,
    s3AccessParams,
    DremioImporterJobConfig(1.0, 512.0)
  )
  lazy val mVHModule = new MVHModule(s3TestPath)
  lazy val dremioExporterJob = new DremioExporterJob(
    taskScheduler,
    new DremioExporterModule,
    dremioAccessParams,
    s3AccessParams,
    DremioExporterJobConfig(1.0, 512.0, 1000000)
  )
  lazy val tabularTrainPredictJob = new TabularJob(
    scheduler             = taskScheduler,
    hyperParamSelector    = hpSelector,
    splitterJob           = splitterJob,
    mvhModule             = mVHModule,
    tabularPipelineModule = tabularModule,
    storageAccessParams   = s3AccessParams,
    tabularJobConfig      = TabularJobConfig(1.0, 512.0, 3, 20),
    modelsBasePath        = modelsBasePath,
    tmpDirPath            = tmpDirPath
  )
  lazy val modelImportModule = new TabularModelImportModule
  lazy val modelImportJob = new TabularModelImportJob(
    scheduler = taskScheduler,
    module    = modelImportModule,
    config    = TabularModelImportJobConfig(1.0, 512.0)
  )
  val s3Client = new S3Client(s3AccessKey, s3SecretKey, s3Region)
  lazy val tabularDataService = new TabularDataService(
    tabularJob          = tabularTrainPredictJob,
    importerJob         = dremioImporterJob,
    exporterJob         = dremioExporterJob,
    storageAccessParams = s3AccessParams,
    modelImportJob      = modelImportJob,
    modelsBasePath      = modelsBasePath,
    tmpDirPath          = tmpDirPath
  )

  val predictors = Seq(
    TableColumn("sex", DataType.STRING, VariableType.CATEGORICAL),
    TableColumn("length", DataType.DOUBLE, VariableType.CONTINUOUS),
    TableColumn("diameter", DataType.DOUBLE, VariableType.CONTINUOUS),
    TableColumn("height", DataType.DOUBLE, VariableType.CONTINUOUS),
    TableColumn("whole_weight", DataType.DOUBLE, VariableType.CONTINUOUS),
    TableColumn("shucked_weight", DataType.DOUBLE, VariableType.CONTINUOUS),
    TableColumn("viscera_weight", DataType.DOUBLE, VariableType.CONTINUOUS),
    TableColumn("shell_weight", DataType.DOUBLE, VariableType.CONTINUOUS)
  )
  val response = TableColumn("logrings", DataType.INTEGER, VariableType.CATEGORICAL)
  val inTrainDataSource = DataSource(
    table = Some(Table(
      meta = Some(TableMeta(
        schema = abaloneTrainTable.schema,
        name = abaloneTrainTable.name
      )),
      columns = predictors ++ Seq(response)
    ))
  )
  val inEvalDataSource = inTrainDataSource.copy(
    table = Some(Table(
      meta = Some(TableMeta(
        schema = abaloneEvaluateTable.schema,
        name = abaloneEvaluateTable.name
      )),
      columns = predictors.map(c => c.copy(name = s"${c.name}_new")) ++
        Seq(response.copy(name = s"${response.name}_new"))
    ))
  )
  val outDataSource = inTrainDataSource.copy(
    table = Some(Table(Some(TableMeta(name = abaloneOutTable.name))))
  )
  var modelId: String = _

  "Tabular data service" should "train/predict" in {
    val jobId = UUID.randomUUID().toString
    val request = TrainRequest(
      input = Some(inTrainDataSource),
      output = Some(outDataSource),
      predictors = predictors,
      response = Some(response),
      dropPreviousResultTable = true
    )
    val (res, _) = tabularDataService.trainPredict(jobId, request).await()
    modelId = res.modelId

    res.formula.isEmpty shouldBe false
    res.modelType.name.isEmpty shouldBe false
    res.predictorsSummary.size shouldBe 10
    res.modelPrimitive.isEmpty shouldBe false
    res.summary.isBinaryClassificationTrainSummary shouldBe true
    res.output.isDefined shouldBe true
  }

  it should "predict" in {
    val jobId = UUID.randomUUID().toString
    val request = PredictRequest(
      modelId = modelId,
      input = Some(inEvalDataSource),
      output = Some(outDataSource),
      predictors = predictors.map(c => ColumnMapping(c.name, s"${c.name}_new")),
      dropPreviousResultTable = true
    )
    val (res, _) = tabularDataService.predict(jobId, request).await()
    res.classProbabilityColumnNames.size shouldBe 2
    res.classProbabilityColumnNames.forall(_.endsWith("$")) shouldBe true
  }

  it should "evaluate" in {
    val jobId = UUID.randomUUID().toString
    val request = EvaluateRequest(
      modelId = "7bf9f76e-6075-413f-82a8-cd88cc7fcc9f/tabular-aeae22f5",
      input = Some(inEvalDataSource),
      output = Some(outDataSource),
      predictors = predictors.map(c => ColumnMapping(c.name, s"${c.name}_new")),
      response = Some(ColumnMapping(response.name, s"${response.name}_new")),
      dropPreviousResultTable = true
    )
    val (res, _) = tabularDataService.evaluate(jobId, request).await()
    res.summary.isBinaryClassificationEvalSummary shouldBe true
  }
}
