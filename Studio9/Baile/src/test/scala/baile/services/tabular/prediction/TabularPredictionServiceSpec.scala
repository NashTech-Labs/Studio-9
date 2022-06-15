package baile.services.tabular.prediction

import java.time.Instant
import java.util.UUID

import baile.dao.tabular.prediction.TabularPredictionDao
import baile.daocommons.WithId
import baile.daocommons.filters.Filter
import baile.domain.asset.AssetType
import baile.domain.common.{ ClassReference, CortexModelReference, Version }
import baile.domain.dcproject.DCProjectPackage
import baile.domain.process.{ Process, ProcessStatus, ResultHandlerMeta }
import baile.domain.table._
import baile.domain.tabular.model.{ ModelColumn, TabularModel, TabularModelStatus }
import baile.domain.tabular.prediction.{ ColumnMapping, TabularPrediction, TabularPredictionStatus }
import baile.domain.usermanagement.User
import baile.services.asset.sharing.AssetSharingService
import baile.services.cortex.job.CortexJobService
import baile.services.cortex.job.SupportedCortexJobTypes.SupportedCortexJobType
import baile.services.dcproject.DCProjectPackageService
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.table.TableService
import baile.services.table.TableService.TableServiceError
import baile.services.tabular.model.TabularModelService.TabularModelServiceError
import baile.services.tabular.model.{ TabularModelCommonService, TabularModelService }
import baile.services.tabular.prediction.TabularPredictionService.TabularPredictionCreateError._
import baile.services.tabular.prediction.TabularPredictionService._
import baile.services.usermanagement.util.TestData.SampleUser
import baile.{ BaseSpec, RandomGenerators }
import cats.implicits._
import cortex.api.job.table.DataSource
import cortex.api.job.tabular.PredictRequest
import org.mockito.ArgumentMatchers.{ any, anyString, eq => eqTo }
import org.mockito.Mockito.when
import play.api.libs.json.{ JsObject, OWrites }

import scala.concurrent.ExecutionContext
import scala.util.Try

class TabularPredictionServiceSpec extends BaseSpec {

  private val mockedTableService: TableService = mock[TableService]
  private val mockedDao: TabularPredictionDao = mock[TabularPredictionDao]
  private val mockedCortexJobService: CortexJobService = mock[CortexJobService]
  private val mockedProcessService: ProcessService = mock[ProcessService]
  private val mockedTabularModelService: TabularModelService = mock[TabularModelService]
  private val mockedTabularModelCommonService: TabularModelCommonService = mock[TabularModelCommonService]
  private val projectService = mock[ProjectService]
  private val packageService = mock[DCProjectPackageService]
  private val assetSharingService = mock[AssetSharingService]
  private val service = new TabularPredictionService(
    tabularModelService = mockedTabularModelService,
    tableService = mockedTableService,
    tabularModelCommonService = mockedTabularModelCommonService,
    dao = mockedDao,
    assetSharingService = assetSharingService,
    cortexJobService = mockedCortexJobService,
    processService = mockedProcessService,
    projectService = projectService,
    packageService = packageService
  )(ec, logger)

  implicit val user: User = SampleUser
  private val dateTime = Instant.now
  private val table = Table(
    ownerId = SampleUser.id,
    name = "name",
    repositoryId = "repositoryId",
    databaseId = "databaseId",
    created = Instant.now(),
    updated = Instant.now(),
    status = TableStatus.Active,
    columns = Seq(Column(
      name = "input",
      displayName = "input",
      dataType = ColumnDataType.Integer,
      variableType = ColumnVariableType.Continuous,
      align = ColumnAlign.Left,
      statistics = None
    )),
    `type` = TableType.Source,
    size = Some(0l),
    inLibrary = true,
    tableStatisticsStatus = TableStatisticsStatus.Pending,
    description = None
  )
  private val predictorColumn = ModelColumn(
    name = "output",
    displayName = "output",
    dataType = ColumnDataType.Integer,
    variableType = ColumnVariableType.Continuous
  )
  private val tabularModel = TabularModel(
    ownerId = SampleUser.id,
    name = "name",
    predictorColumns = Seq(predictorColumn),
    responseColumn = ModelColumn(
      name = "name",
      displayName = "name",
      dataType = ColumnDataType.Integer,
      variableType = ColumnVariableType.Continuous
    ),
    classNames = None,
    classReference = ClassReference(
      moduleName = RandomGenerators.randomString(),
      className = RandomGenerators.randomString(),
      packageId = RandomGenerators.randomString()
    ),
    cortexModelReference = Some(CortexModelReference(
      cortexId = "id",
      cortexFilePath = "filePath"
    )),
    inLibrary = true,
    status = TabularModelStatus.Active,
    created = Instant.now(),
    updated = Instant.now(),
    description = None,
    experimentId = Some(randomString())
  )
  private val columnMappings = Seq(ColumnMapping(
    trainName = "output",
    currentName = "input"
  ))
  private val allowedDataTypes: Set[ColumnDataType] = Set(ColumnDataType.Integer, ColumnDataType.String)
  private val prediction = WithId(
    TabularPrediction(
      ownerId = user.id,
      name = "predict-name",
      created = dateTime,
      updated = dateTime,
      status = TabularPredictionStatus.Running,
      modelId = "model-id",
      inputTableId = "input-table",
      outputTableId = "output-id",
      columnMappings = Seq(
        ColumnMapping(
          trainName = "output",
          currentName = "input"
        )
      ),
      description = Some("description")
    ),
    "id"
  )

  private val dcProjectPackageWithId = WithId(
    DCProjectPackage(
      ownerId = Some(user.id),
      dcProjectId = Some(randomString()),
      name = randomString(),
      version = Some(Version(1, 0, 0, None)),
      location = Some(randomPath()),
      created = Instant.now(),
      description = None,
      isPublished = true
    ),
    randomString()
  )
  when(mockedDao.count(any[Filter])(any[ExecutionContext])) thenReturn future(0)
  when(mockedTabularModelService.get(anyString())(any[User])) thenReturn {
    future(
      WithId(tabularModel, "id").asRight
    )
  }

  when(mockedTableService.get(anyString())(any[User])) thenReturn {
    future(
      WithId(table, "table-id").asRight
    )
  }
  when(mockedTableService.buildDataSource(any[Table])) thenReturn {
    Try(DataSource(None))
  }
  when(mockedTabularModelCommonService.getCortexId(any[WithId[TabularModel]])) thenReturn {
    Try(
      "cortexId"
    )
  }
  when(mockedTableService.createEmptyTable(
    any[Option[String]],
    any[TableType],
    any[Seq[Column]],
    any[Boolean],
    any[User],
    any[Option[String]]
  )) thenReturn future(WithId(table, "output-id"))

  when(mockedDao.create(any[TabularPrediction])(any[ExecutionContext])) thenReturn {
    future("id")
  }
  when(mockedCortexJobService.submitJob(
    any[PredictRequest],
    any[UUID]
  )(eqTo(implicitly[SupportedCortexJobType[PredictRequest]]))) thenReturn {
    future(
      UUID.fromString("42669c4f-668a-4dca-b312-f46acd71d53f")
    )
  }
  when(mockedTabularModelCommonService.generatePredictionResultColumnName(any[String], any[Seq[String]])) thenReturn {
    "prediction"
  }
  when(mockedTabularModelCommonService.generateProbabilityColumnsForClasses(tabularModel, table)).thenReturn(Seq.empty)
  when(mockedTableService.allowedTypeConversions(any[ColumnDataType])) thenReturn allowedDataTypes
  when(mockedProcessService.startProcess(
    any[UUID],
    any[String],
    any[AssetType],
    any[Class[TabularPredictionResultHandler]],
    any[TabularPredictionResultHandler.Meta],
    any[UUID],
    any[Option[String]]
  )(any[OWrites[TabularPredictionResultHandler.Meta]])) thenReturn {
    future(
      WithId(
        Process(
          targetId = "target-id",
          targetType = AssetType.TabularPrediction,
          ownerId = UUID.fromString("42669c4f-668a-4dca-b312-f46acd71d53f"),
          authToken = None,
          jobId = UUID.fromString("42669c4f-668a-4dca-b312-f46acd71d53f"),
          status = ProcessStatus.Running,
          progress = None,
          estimatedTimeRemaining = None,
          created = dateTime,
          started = None,
          completed = None,
          errorCauseMessage = None,
          errorDetails = None,
          onComplete = ResultHandlerMeta(
            "class-name",
            JsObject.empty
          ),
          auxiliaryOnComplete = Seq.empty
        ),
        "uu-id"
      )
    )
  }
  when(
    packageService.get(eqTo(tabularModel.classReference.packageId))(eqTo(user))
  ).thenReturn(future(dcProjectPackageWithId.asRight))

  "TabularPredictionServiceSpec#Create" should {

    "predict without any errors/exception" in {
      when(mockedDao.count(any[Filter])(any[ExecutionContext])) thenReturn future(0)
      whenReady(service.create(
        Some("predict-name"),
        "model-id",
        "input-table",
        columnMappings,
        Some("description")
      )) { result =>
        assert(result.isRight)
        val res: WithId[TabularPrediction] = WithId(
          result.right.get.entity.copy(created = dateTime, updated = dateTime),
          result.right.get.id
        )
        res shouldBe prediction
      }
    }

    "return an error when predictor not found" in {
      when(mockedDao.count(any[Filter])(any[ExecutionContext])) thenReturn future(0)
      whenReady(service.create(
        Some("predict-name"),
        "model-id",
        "input-table",
        Seq(ColumnMapping(
          trainName = "xyz",
          currentName = "input"
        )),
        None
      )) { result =>
        result shouldBe Left(PredictorColumnMissing(predictorColumn))
      }
    }

    "return an error when Column Mapping has non-unique values" in {
      whenReady(service.create(
        Some("predict-name"),
        "model-id",
        "input-table",
        Seq(
          ColumnMapping(
            trainName = "xyz",
            currentName = "input"
          ),
          ColumnMapping(
            trainName = "pqr",
            currentName = "input"
          )
        ),
        None
      )) { result =>
        result shouldBe Left(ColumnMappingNotUnique)
      }
    }

    "return an error when predictor not found in input table" in {
      val columnMapping = ColumnMapping(
        trainName = "output",
        currentName = "xyz"
      )
      when(mockedDao.count(any[Filter])(any[ExecutionContext])) thenReturn future(0)
      whenReady(service.create(
        Some("predict-name"),
        "model-id",
        "input-table",
        Seq(columnMapping),
        None
      )) { result =>
        result shouldBe Left(PredictorNotFoundInTable(columnMapping, table))
      }
    }

    "return an error when invalid data type of predictor column in input table" in {
      val predictorColumn = ModelColumn(
        name = "output",
        displayName = "output",
        dataType = ColumnDataType.Boolean,
        variableType = ColumnVariableType.Continuous
      )
      when(mockedTabularModelService.get(anyString())(any[User])) thenReturn {
        future(
          WithId(tabularModel.copy(predictorColumns = Seq(predictorColumn)), "id").asRight
        )
      }
      whenReady(service.create(
        Some("predict-name"),
        "model-id",
        "input-table",
        columnMappings,
        None
      )) { result =>
        result shouldBe Left(InvalidColumnDataType(predictorColumn, allowedDataTypes))
      }
    }

    "return error when name is empty" in {
      whenReady(service.create(
        Some(""),
        "model-id",
        "input-table",
        columnMappings,
        None
      )) { result =>
        result shouldBe Left(PredictionNameCanNotBeEmpty)
      }
    }

    "return an error when prediction already exist" in {
      when(mockedDao.count(any[Filter])(any[ExecutionContext])) thenReturn future(22)
      whenReady(service.create(
        Some("predict-name"),
        "model-id",
        "input-table",
        columnMappings,
        None
      )) { result =>
        assert(result.isLeft)
        result shouldBe Left(TabularPredictionCreateError.TabularPredictionNameAlreadyExists("predict-name"))
      }
    }

    "return an error when input table not found" in {
      when(mockedDao.count(any[Filter])(any[ExecutionContext])) thenReturn future(0)
      when(mockedTableService.get(anyString())(any[User])) thenReturn {
        future(
          TableServiceError.TableNotFound.asLeft
        )
      }
      whenReady(service.create(
        Some("predict-name"),
        "model-id",
        "input-table",
        columnMappings,
        None
      )) { result =>
        result shouldBe Left(TableNotFound)
      }
    }

    "return an error when tabular model not found" in {
      when(mockedDao.count(any[Filter])(any[ExecutionContext])) thenReturn future(0)
      when(mockedTabularModelService.get(anyString())(any[User])) thenReturn {
        future(
          TabularModelServiceError.ModelNotFound.asLeft
        )
      }
      whenReady(service.create(
        Some("predict-name"),
        "model-id",
        "input-table",
        columnMappings,
        None
      )) { result =>
        result shouldBe Left(TabularModelNotFound)
      }
    }

    "return an error when column mappings are not found" in {
      whenReady(service.create(
        Some("predict-name"),
        "model-id",
        "input-table",
        Seq(),
        None
      )) { result =>
        result shouldBe Left(ColumnMappingNotFound)
      }
    }

    "return an error when tabular model is not active" in {
      when(mockedTabularModelService.get(anyString())(any[User])) thenReturn {
        future(
          WithId(tabularModel.copy(status = TabularModelStatus.Cancelled), "id").asRight
        )
      }
      whenReady(service.create(
        Some("predict-name"),
        "model-id",
        "input-table",
        columnMappings,
        None
      )) { result =>
        result shouldBe Left(TabularModelNotActive)
      }
    }

  }

  "TabularPredictionServiceSpec#savePrediction" should {

    "save without error" in {
      when(mockedDao.get(anyString())(any[ExecutionContext])) thenReturn
        future(
          Some(WithId(
            prediction.entity.copy(status = TabularPredictionStatus.Done),
            prediction.id
          ))
        )
      when(mockedTableService.update(anyString, any[Table => Table].apply)(any[User])) thenReturn
        future(WithId(table, "table-id").asRight)
      whenReady(service.save("test")) { result =>
        result shouldBe Right(())
      }
    }

    "return error when prediction not complete" in {
      when(mockedDao.get(anyString())(any[ExecutionContext])) thenReturn future(Some(prediction))
      when(mockedTableService.update(anyString, any[Table => Table].apply)(any[User])) thenReturn
        future(WithId(table, "table-id").asRight)
      whenReady(service.save("test")) { result =>
        result shouldBe Left(TabularPredictionServiceError.PredictionNotComplete)
      }
    }

  }

  "TabularPredictionServiceSpec#update" should {

    "update without error" in {
      when(mockedDao.count(any[Filter])(any[ExecutionContext])) thenReturn future(0)
      when(mockedDao.update(
        anyString,
        any[TabularPrediction => TabularPrediction].apply
      )(any[ExecutionContext])) thenReturn {
        future(
          Some(prediction)
        )
      }
      whenReady(service.update("id", Some("newName"), Some("description"))) { result =>
        result shouldBe Right(prediction)
      }
    }

    "return an error when name is empty" in {
      whenReady(service.update("id", Some(""), None)) { result =>
        result shouldBe Left(TabularPredictionServiceError.PredictionNameCanNotBeEmpty)
      }
    }

    "return an error when name already exists" in {
      when(mockedDao.count(any[Filter])(any[ExecutionContext])) thenReturn future(2)
      whenReady(service.update("id", Some("name"), None)) { result =>
        result shouldBe Left(TabularPredictionServiceError.TabularPredictionNameAlreadyExists("name"))
      }
    }

  }

}
