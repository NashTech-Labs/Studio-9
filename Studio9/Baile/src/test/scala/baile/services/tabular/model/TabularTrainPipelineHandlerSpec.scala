package baile.services.tabular.model

import java.time.Instant
import java.util.UUID

import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.table.TableDao
import baile.dao.tabular.model.TabularModelDao
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.common.{ CortexModelReference, Version }
import baile.domain.dcproject.DCProjectPackage
import baile.domain.experiment.{ Experiment, ExperimentStatus }
import baile.domain.table.{ ColumnDataType, ColumnVariableType, TableStatus }
import baile.domain.tabular.model.{ TabularModel, TabularModelStatus }
import baile.domain.tabular.pipeline.TabularTrainPipeline
import baile.domain.tabular.result.TabularTrainResult
import baile.domain.usermanagement.RegularUser
import baile.services.cortex.job.CortexJobService
import baile.services.dcproject.DCProjectPackageService
import baile.services.process.ProcessService
import baile.services.process.util.ProcessRandomGenerator.randomProcess
import baile.services.table.TableService
import baile.services.table.util.TableRandomGenerator._
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.tabular.model.TabularModelRandomGenerator._
import baile.services.tabular.model.TabularTrainPipelineHandler.{ EvaluationParams, EvaluationType, TypedEvaluationParams }
import cats.implicits._
import cortex.api.job.table.DataSource
import cortex.api.job.tabular.{ EvaluateRequest, TrainRequest }

import scala.util.{ Failure, Try }

class TabularTrainPipelineHandlerSpec extends ExtendedBaseSpec {

  implicit val user: RegularUser = SampleUser

  trait Setup {

    val modelDao = mock[TabularModelDao]
    val tableDao = mock[TableDao]
    val modelService = mock[TabularModelService]
    val tableService = mock[TableService]
    val tabularModelCommonService = mock[TabularModelCommonService]
    val cortexJobService = mock[CortexJobService]
    val processService = mock[ProcessService]
    val packageService = mock[DCProjectPackageService]
    val tabularConfig = conf.getConfig("tabular-models")

    val pipelineHandler = new TabularTrainPipelineHandler(
      modelDao,
      tableDao,
      modelService,
      tableService,
      tabularModelCommonService,
      cortexJobService,
      processService,
      packageService,
      tabularConfig
    )

  }


  "TabularTrainPipelineHandler#validateAndCreatePipeline" should {

    trait CreateSetup extends Setup {

      val token = randomString()

      val sampleWeightColumn = randomColumn(
        dataType = ColumnDataType.Double,
        variableType = ColumnVariableType.Continuous
      )

      val inputTable = randomTable(
        ownerId = user.id,
        status = TableStatus.Active,
        columns = sampleWeightColumn +: Seq.fill(5)(randomColumn())
      )
      val outputTable = randomTable(
        ownerId = user.id,
        status = TableStatus.Saving
      )

      val model = TabularModelRandomGenerator.randomModel(ownerId = user.id)

      val projectPackage = WithId(
        DCProjectPackage(
          ownerId = Some(user.id),
          dcProjectId = None,
          name = "package",
          location = None,
          version = Some(Version(1, 0, 0, None)),
          created = Instant.now(),
          description = None,
          isPublished = true
        ),
        randomString()
      )

    }

    "successfully create pipeline" in new CreateSetup {

      val pipeline = TabularTrainPipeline(
        samplingWeightColumnName = Some(sampleWeightColumn.name),
        predictorColumns = inputTable.entity.columns.take(2).map(buildModelColumn).toList,
        responseColumn = buildModelColumn(inputTable.entity.columns.last),
        inputTableId = inputTable.id,
        holdOutInputTableId = None,
        outOfTimeInputTableId = None
      )

      val experiment = WithId(
        Experiment(
          name = "experiment",
          ownerId = user.id,
          description = None,
          status = ExperimentStatus.Running,
          pipeline = pipeline,
          result = None,
          created = Instant.now(),
          updated = Instant.now()
        ),
        randomString()
      )

      tableService.get(inputTable.id)(user) shouldReturn future(inputTable.asRight)
      modelDao.count(*) shouldReturn future(0)
      tableService.allowedTypeConversions(*) shouldCall realMethod
      packageService.getPackageByNameAndVersion(
        tabularConfig.getString("package-name"),
        None
      ) shouldReturn future(Some(projectPackage))
      tabularModelCommonService.createOutputTable(None, List.empty, user) shouldReturn future(outputTable)
      tableService.buildDataSource(inputTable.entity) shouldReturn Try(DataSource())
      tableService.buildDataSource(outputTable.entity) shouldReturn Try(DataSource())
      modelDao.create(*[TabularModel])(*) shouldReturn future(model.id)
      cortexJobService.submitJob(*[TrainRequest], user.id) shouldReturn future(UUID.randomUUID())
      processService.startProcess(
        *,
        experiment.id,
        AssetType.Experiment,
        *[Class[TabularModelTrainResultHandler]],
        *[TabularModelTrainResultHandler.Meta],
        user.id
      ) shouldReturn future(randomProcess(
        targetId = experiment.id,
        targetType = AssetType.Experiment
      ))
      tabularModelCommonService.probabilityColumnsPrefix shouldReturn "prefix"
      tableService.dataTypeToCortexDataType(*) shouldCall realMethod
      tabularModelCommonService.generatePredictionResultColumnName(*, *) shouldCall realMethod
      tableService.variableTypeToCortexVariableType(*) shouldCall realMethod

      whenReady(
        pipelineHandler.validateAndCreatePipeline(
          pipeline = pipeline,
          experimentName = experiment.entity.name,
          experimentDescription = None
        )
      ) { result =>
        val experimentHandler = result.right.value.handler
        whenReady(experimentHandler(experiment.id)) { process =>
          process.entity.targetId shouldBe experiment.id
        }
      }

    }

  }

  "TabularTrainPipelineHandler#launchEvaluation" should {

    val model = randomModel(
      cortexModelReference = Some(CortexModelReference(
        randomString(),
        randomString()
      ))
    )
    val testInputTable = randomTable()
    val testOutputTable = randomTable()
    val experimentId = randomString()

    "successfully start evaluation" in new Setup {
      val cortexJobId = UUID.randomUUID()
      val projectPackage = WithId(
        DCProjectPackage(
          ownerId = Some(user.id),
          dcProjectId = None,
          name = "package",
          location = None,
          version = Some(Version(1, 0, 0, None)),
          created = Instant.now(),
          description = None,
          isPublished = true
        ),
        randomString()
      )
      tabularModelCommonService.getCortexId(model) shouldReturn Try(model.entity.cortexModelReference.get.cortexId)
      tableService.loadTableMandatory(testInputTable.id) shouldReturn future(testInputTable)
      tableService.loadTableMandatory(testOutputTable.id) shouldReturn future(testOutputTable)
      packageService.loadPackageMandatory(model.entity.classReference.packageId) shouldReturn future(projectPackage)
      tabularModelCommonService.generatePredictionResultColumnName(
        model.entity.responseColumn.name,
        *
      ) shouldReturn randomString()
      tabularModelCommonService.generateProbabilityColumnsForClasses(
        model.entity,
        testInputTable.entity
      ) shouldCall realMethod
      tableService.getColumnAlignment(*) shouldCall realMethod
      tableService.updateTable(testOutputTable.id, *) shouldReturn future(testOutputTable)
      tableService.buildDataSource(testInputTable.entity) shouldReturn Try(DataSource())
      tableService.buildDataSource(testOutputTable.entity) shouldReturn Try(DataSource())
      cortexJobService.submitJob(*[EvaluateRequest], user.id)(*) shouldReturn future(cortexJobId)
      processService.startProcess(
        jobId = cortexJobId,
        targetId = experimentId,
        targetType = AssetType.Experiment,
        handlerClass = classOf[TabularModelEvaluateResultHandler],
        meta = TabularModelEvaluateResultHandler.Meta(
          experimentId = experimentId,
          evaluationType = EvaluationType.HoldOut,
          nextStepParams = None,
          outputTableId = testOutputTable.id,
          userId = user.id
        ),
        userId = user.id
      ) shouldReturn future(randomProcess())

      pipelineHandler.launchEvaluation(
        model = model,
        experimentId = experimentId,
        evaluationParams = TypedEvaluationParams(
          EvaluationParams(testInputTable.id, testOutputTable.id),
          EvaluationType.HoldOut
        ),
        nextStepEvaluationParams = None,
        samplingWeightColumnName = None,
        userId = user.id
      ).futureValue
    }

    "fail when model does not contain cortex id" in new Setup {
      tabularModelCommonService.getCortexId(model) shouldReturn Failure(new RuntimeException)
      pipelineHandler.launchEvaluation(
        model = model,
        experimentId = experimentId,
        evaluationParams = TypedEvaluationParams(
          EvaluationParams(testInputTable.id, testOutputTable.id),
          EvaluationType.HoldOut
        ),
        nextStepEvaluationParams = None,
        samplingWeightColumnName = None,
        userId = user.id
      ).failed.futureValue should not be a[NullPointerException]
    }

  }

  "TabularTrainPipelineHandler#updateOutputEntitiesOnSuccess" should {

    "make all tables and model active" in new Setup {

      val model = randomModel(inLibrary = false)
      val outputTable = randomTable(inLibrary = false)
      val holdOutOutputTable = randomTable(inLibrary = false)
      val outOfTimeOutputTable = randomTable(inLibrary = false)

      val result = TabularTrainResult(
        modelId = model.id,
        outputTableId = outputTable.id,
        holdOutOutputTableId = Some(holdOutOutputTable.id),
        outOfTimeOutputTableId = Some(outOfTimeOutputTable.id),
        predictedColumnName = randomString(),
        classes = None,
        summary = None,
        holdOutSummary = None,
        outOfTimeSummary = None,
        predictorsSummary = Seq.empty
      )

      tabularModelCommonService.updateModelStatus(result.modelId, TabularModelStatus.Active) shouldReturn future(model)
      tableService.updateStatus(outputTable.id, TableStatus.Active) shouldReturn future(())
      tableService.updateStatus(holdOutOutputTable.id, TableStatus.Active) shouldReturn future(())
      tableService.updateStatus(outOfTimeOutputTable.id, TableStatus.Active) shouldReturn future(())

      pipelineHandler.updateOutputEntitiesOnSuccess(result).futureValue
    }

  }

  "TabularTrainPipelineHandler#updateOutputEntitiesOnNoSuccess" should {

    "make all tables failed and model cancelled" in new Setup {

      val model = randomModel(inLibrary = false)
      val outputTable = randomTable(inLibrary = false)
      val holdOutOutputTable = randomTable(inLibrary = false)
      val outOfTimeOutputTable = randomTable(inLibrary = false)

      val result = TabularTrainResult(
        modelId = model.id,
        outputTableId = outputTable.id,
        holdOutOutputTableId = Some(holdOutOutputTable.id),
        outOfTimeOutputTableId = Some(outOfTimeOutputTable.id),
        predictedColumnName = randomString(),
        classes = None,
        summary = None,
        holdOutSummary = None,
        outOfTimeSummary = None,
        predictorsSummary = Seq.empty
      )

      tabularModelCommonService.updateModelStatus(result.modelId, TabularModelStatus.Cancelled) shouldReturn future(model)
      tabularModelCommonService.failTable(outputTable.id) shouldReturn future(())
      tabularModelCommonService.failTable(holdOutOutputTable.id) shouldReturn future(())
      tabularModelCommonService.failTable(outOfTimeOutputTable.id) shouldReturn future(())

      pipelineHandler.updateOutputEntitiesOnNoSuccess(result, ExperimentStatus.Cancelled).futureValue
    }

  }



}
