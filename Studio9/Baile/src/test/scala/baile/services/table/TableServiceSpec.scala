package baile.services.table

import java.time.Instant
import java.util.UUID

import akka.http.caching.scaladsl.Cache
import akka.stream.scaladsl.Source
import baile.BaseSpec
import baile.dao.asset.Filters.NameIs
import baile.dao.table.{ TableDao, TableDataDao }
import baile.daocommons.WithId
import baile.daocommons.filters.{ Filter, IdIn }
import baile.daocommons.sorting.SortBy
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.process.{ Process, ProcessStatus, ResultHandlerMeta }
import baile.domain.table.TableRowValue.StringValue
import baile.domain.table._
import baile.domain.usermanagement.User
import baile.services.asset.sharing.AssetSharingService
import baile.services.cortex.job.CortexJobService
import baile.services.cortex.job.SupportedCortexJobTypes.SupportedCortexJobType
import baile.services.process.ProcessService
import baile.services.process.ProcessService.ProcessNotFoundError
import baile.services.project.ProjectService
import baile.services.table.TableService._
import baile.services.table.util.TestData._
import baile.services.usermanagement.util.TestData.SampleUser
import cortex.api.job.table.{ TableUploadRequest, TabularColumnStatisticsRequest }
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json.{ JsObject, Json, OWrites }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TableServiceSpec extends BaseSpec with TableDrivenPropertyChecks {

  val assetSharingService: AssetSharingService = mock[AssetSharingService]
  val mockedCache: Cache[String, Int] = mock[Cache[String, Int]]
  val mockedDao: TableDao = mock[TableDao]
  val mockedProcessService: ProcessService = mock[ProcessService]
  val cortexJobService: CortexJobService = mock[CortexJobService]
  val tableDataDao: TableDataDao = mock[TableDataDao]
  private val projectService = mock[ProjectService]


  implicit private val user: User = SampleUser
  val service = new TableService(
    conf,
    tableDataDao,
    assetSharingService,
    mockedCache,
    mockedDao,
    mockedProcessService,
    cortexJobService,
    projectService
  )(ec, logger)
  val table = WithId(TableEntity, "id")

  "TableService#delete" should {

    "delete table without any errors" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      when(projectService.removeAssetFromAllProjects(any[AssetReference])(any[User])).thenReturn(future(()))
      when(mockedDao.delete(any[String])(any[ExecutionContext])).thenReturn(future(true))
      doNothing().when(mockedCache).remove(any[String])
      when(assetSharingService.deleteSharesForAsset(any[String], any[AssetType])(any[User])).thenReturn(future(()))
      when(mockedProcessService.cancelProcesses(any[String], eqTo(AssetType.Table))(any[User]))
        .thenReturn(future(Right(())))
      service.delete("tableId").futureValue
    }

    "delete table with TableNotFound Error" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(None))
      service.delete("tableId").futureValue
    }

  }

  "TableService#cloneTable" should {

    "clone table without any errors" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      when(mockedDao.count(filterContains(NameIs("newname")))(any[ExecutionContext])).thenReturn(future(0))
      when(mockedDao.create(any[String => Table])(any[ExecutionContext])).thenReturn(future(table))
      whenReady(service.cloneTable("newname", "tableId")) { result =>
        assert(result.isRight)
        assert(result.right.get == table)
      }
    }

    "clone table with any errors when table already cloned twice" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      when(mockedDao.count(filterContains(NameIs("newname")))(any[ExecutionContext])).thenReturn(future(1))
      when(mockedDao.create(any[String => Table])(any[ExecutionContext])).thenReturn(future(table))
      whenReady(service.cloneTable("newname", "tableId")) { result =>
        assert(result.isLeft)
      }
    }

    "give error if table name is empty while cloning a table " in {
      whenReady(service.cloneTable("", "tableId")) { result =>
        assert(result.isLeft)
        assert(result.left.get == TableServiceError.TableNameIsEmpty)
      }
    }

    "give error if table is not active with number while cloning a table " in {
      val tableEntityWithId = WithId(TableEntity.copy(status = TableStatus.Saving), "id")
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(tableEntityWithId)))
      whenReady(service.cloneTable("abc", "tableId")) { result =>
        assert(result.isLeft)
        assert(result.left.get == TableServiceError.TableIsNotActive)
      }
    }

  }

  "TableService#updateTable" should {

    "update table with new name" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      when(mockedDao.count(filterContains(NameIs("new-name")))(any[ExecutionContext])).thenReturn(future(0))
      when(mockedDao.update(any[String], any[Table => Table].apply)(any[ExecutionContext])) thenReturn future(
        Some(table)
      )
      val jobId = UUID.randomUUID()
      val onComplete: ResultHandlerMeta = ResultHandlerMeta(
        handlerClassName = "MyClassName",
        meta = JsObject(Seq.empty)
      )
      when(cortexJobService.submitJob(
        any[TabularColumnStatisticsRequest],
        any[UUID]
      )(eqTo(implicitly[SupportedCortexJobType[TabularColumnStatisticsRequest]]))).thenReturn(future(jobId))
      when(mockedProcessService.startProcess(
        any[UUID],
        any[String],
        any[AssetType],
        any[Class[ColumnStatisticsResultHandler]],
        any[ColumnStatisticsResultHandler.Meta],
        any[UUID],
        any[Option[String]]
      )(eqTo(implicitly[OWrites[ColumnStatisticsResultHandler.Meta]]))).thenReturn(future(WithId(Process(
        targetId = table.id,
        targetType = AssetType.Table,
        ownerId = user.id,
        authToken = None,
        jobId = UUID.randomUUID,
        status = ProcessStatus.Running,
        progress = Some(1),
        estimatedTimeRemaining = Some(1e5.seconds),
        created = Instant.now,
        started = None,
        completed = None,
        onComplete = onComplete,
        errorCauseMessage = None,
        errorDetails = None,
        auxiliaryOnComplete = Seq.empty
      ), "foo")))
      val columnRequest = Seq(
        UpdateColumnParams(
          "name", Some("display"), Some(ColumnVariableType.Continuous), Some(ColumnAlign.Left)
        )
      )

      whenReady(service.updateTable("tableid", Some("new-name"), Some("description"), columnRequest)) { result =>
        assert(result.isRight)
      }
    }

    "unable to update table when updating column type as continuous when datatype is string" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(
        Some(WithId(
          table.entity.copy(columns = Seq(ColumnEntity.copy(dataType = ColumnDataType.String))),
          table.id
        ))
      ))
      when(mockedDao.count(filterContains(NameIs("new-name")))(any[ExecutionContext])).thenReturn(future(0))
      when(mockedDao.update(any[String], (any[Table => Table]).apply)(any[ExecutionContext])) thenReturn future(
        Some(WithId(
          table.entity.copy(columns = Seq(ColumnEntity.copy(dataType = ColumnDataType.String))),
          table.id
        ))
      )

      val columnRequest = Seq(
        UpdateColumnParams(
          "name", Some("display"), Some(ColumnVariableType.Continuous), Some(ColumnAlign.Left)
        )
      )
      whenReady(service.updateTable("tableid", Some("new-name"), None, columnRequest)) { result =>
        assert(result.isLeft)
        assert(result.left.get == TableServiceError.InvalidContinuousDataType)
      }
    }

    "unable to update table when updating column is missing in table" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(
        Some(table)
      ))
      when(mockedDao.count(filterContains(NameIs("new-name")))(any[ExecutionContext])).thenReturn(future(0))
      when(mockedDao.update(any[String], any[Table => Table].apply)(any[ExecutionContext])) thenAnswer {
        m => future(
          Some(WithId(
            m.getArgument[Table => Table](1)(table.entity),
            m.getArgument[String](0)
          ))
        )
      }

      val columnRequest = Seq(
        UpdateColumnParams(
          "nameNotInTable", Some(randomString()), None, None
        )
      )
      whenReady(service.updateTable("tableid", Some("new-name"), None, columnRequest)) { result =>
        assert(result.isLeft)
        assert(result.left.get == TableServiceError.TableDoesNotHaveSuchColumn)
      }
    }

    "unable to update table when new name is not unique" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      when(mockedDao.count(filterContains(NameIs("new-name")))(any[ExecutionContext])).thenReturn(future(1))
      whenReady(service.updateTable("tableid", Some("new-name"), None, Nil)) { result =>
        assert(result.isLeft)
      }
    }

  }

  "TableService#exportTableRow" should {

    "export table data" in {
      when(tableDataDao.getTableRowSource(any[Table])(any[ExecutionContext])).thenReturn{
        future(Source.single(TableRow(Seq(StringValue("test")))))
      }
      whenReady(service.export("tableId", None)){ result =>
        assert(result.isRight)
      }
    }

  }

  "TableService#uploadTable" should {

    "upload file and create table" in {
      when(mockedDao.count(filterContains(NameIs("name")))(any[ExecutionContext])).thenReturn(future(0))
      when(mockedDao.create(any[String => Table])(any[ExecutionContext])).thenReturn(future(table))
      val jobId = UUID.randomUUID()
      val onComplete: ResultHandlerMeta = ResultHandlerMeta(
        handlerClassName = "MyClassName",
        meta = JsObject(Seq.empty)
      )
      when(cortexJobService.submitJob(
        any[TableUploadRequest],
        any[UUID]
      )(eqTo(implicitly[SupportedCortexJobType[TableUploadRequest]]))).thenReturn(future(jobId))
      when(mockedProcessService.startProcess(
        any[UUID],
        any[String],
        any[AssetType],
        any[Class[TableUploadResultHandler]],
        any[TableUploadResultHandler.Meta],
        any[UUID],
        any[Option[String]]
      )(eqTo(implicitly[OWrites[TableUploadResultHandler.Meta]]))).thenReturn(future(WithId(Process(
        targetId = table.id,
        targetType = AssetType.Table,
        ownerId = user.id,
        authToken = None,
        jobId = UUID.randomUUID,
        status = ProcessStatus.Running,
        progress = Some(1),
        estimatedTimeRemaining = Some(1e5.seconds),
        created = Instant.now,
        started = None,
        completed = None,
        onComplete = onComplete,
        errorCauseMessage = None,
        errorDetails = None,
        auxiliaryOnComplete = Seq.empty
      ), "foo")))
      whenReady(service.uploadTable(
        Map("name" -> "name"), FileType.JSON, "/randomPath", ",", None, None, None
      )) { result =>
        assert(result.isRight)
      }
    }

    "not be able to create table if name is not unique" in {
      when(mockedDao.count(filterContains(NameIs("name")))(any[ExecutionContext])).thenReturn(future(1))
      whenReady(service.uploadTable(
        Map("name" -> "name"), FileType.JSON, "/randomPath", ",", None, None, None
      )) { result =>
        assert(result.isLeft)
      }
    }

    "error if columns names not unique" in {
      val columnsData = Table(
        "columnNames",
        Seq("foo" -> None, "foo" -> None),
        Seq("foo" -> Some("any"), "foo" -> Some("Other")),
        Seq("foo" -> Some("same"), "bar" -> Some("same")),
        Seq("foo" -> None, "bar" -> Some("foo")),
      )

      forAll(columnsData) { names =>
        val columns = names.map {
          case (name, displayName) => ColumnInfo(
            name = name,
            displayName = displayName,
            variableType = None,
            dataType = ColumnDataType.String
          )
        }
        whenReady(service.uploadTable(
          Map("name" -> "name"),
          FileType.JSON,
          "/randomPath",
          ",",
          None,
          None,
          Some(columns)
        )) { result =>
          assert(result.isLeft)
        }
      }
    }
  }

  "TableService#getTableData" should {
    "return success response" in {
      val tableRow = TableRow(Seq(StringValue("xyz")))
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      when(tableDataDao.getRows(
        any[Table],
        any[Int],
        any[Int],
        any[Option[Filter]],
        any[Option[SortBy]]
      )(any[ExecutionContext])).thenReturn(future(Seq(tableRow)))
      when(tableDataDao.getRowsCount(any[Table], any[Option[Filter]])(any[ExecutionContext])) thenReturn future(10L)
      whenReady(service.getTableData("id", 10, 10, None, Some("name"), None)) { result =>
        assert(result.isRight)
        result shouldBe Right((List(TableRow(List(StringValue("xyz")))), 10L))
      }
    }

    "return error response when table status is not active" in {
      val table = WithId(TableEntity.copy(status =TableStatus.Inactive), "id")
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      whenReady(service.getTableData("id", 10, 10, None, Some("name"), None)) { result =>
        assert(result.isLeft)
        assert(result.left.get === TableServiceError.TableIsNotActive)
      }
    }

    "return error response when column name not found in table" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      whenReady(service.getTableData("id", 10, 10, None, Some("xyz"), None)) { result =>
        assert(result.isLeft)
        assert(result.left.get === TableServiceError.ColumnNotFound("xyz"))
      }
    }
  }

  "TableService#getColumnValues" should {

    "return success response" in {
      val column = ColumnEntity.copy(dataType = ColumnDataType.String)
      val entity = TableEntity.copy(columns = Seq(column))
      val table = WithId(entity, "id")
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      when(tableDataDao.getColumnValues(
        any[Table],
        any[String],
        any[Int],
        any[Option[Filter]]
      )(any[ExecutionContext])).thenReturn(future(Seq(TableRowValue.StringValue("value"))))
      when(tableDataDao.getRowsCount(any[Table], any[Option[Filter]])(any[ExecutionContext])) thenReturn future(10L)
      whenReady(service.getColumnValues("id", "name", Some("xyz"), 1, None)) { result =>
        result shouldBe Right(Seq(TableRowValue.StringValue("value")))
      }
    }

    "return success response when table column type is boolean" in {
      val column = ColumnEntity.copy(dataType = ColumnDataType.Boolean)
      val entity = TableEntity.copy(columns = Seq(column))
      val table = WithId(entity, "id")
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      when(tableDataDao.getColumnValues(
        any[Table],
        any[String],
        any[Int],
        any[Option[Filter]]
      )(any[ExecutionContext])).thenReturn(future(Seq(TableRowValue.StringValue("value"))))
      when(tableDataDao.getRowsCount(any[Table], any[Option[Filter]])(any[ExecutionContext])) thenReturn future(10L)
      whenReady(service.getColumnValues("id", "name", Some("true"), 1, None)) { result =>
        result shouldBe Right(Seq(TableRowValue.StringValue("value")))
      }
    }

    "return error response when column name not found in table" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      whenReady(service.getColumnValues("id", "xyz", Some("searchParam"), 10, None)) { result =>
        assert(result.isLeft)
        assert(result.left.get === TableServiceError.ColumnNotFound("xyz"))
      }
    }
  }

  "TableService#save" should {

    "should save the table successfully" in {
      when(mockedDao.count(filterContains(NameIs("name")))(any[ExecutionContext])).thenReturn(future(0))
      whenReady(service.save("id", "name")) { result =>
        result shouldBe Right(table)
      }
    }

    "return error response when table name already exists" in {
      when(mockedDao.count(filterContains(NameIs("name")))(any[ExecutionContext])).thenReturn(future(1))
      whenReady(service.save("id", "name")) { result =>
        result shouldBe Left(TableServiceError.TableNameIsNotUnique("name"))
      }
    }

    "return error response when table name is empty" in {
      whenReady(service.save("id", "")) { result =>
        result shouldBe Left(TableServiceError.TableNameIsEmpty)
      }
    }
  }

  "TableService#getStatistics" should {

    "return statistics for table" in {
      val categoricalColumn = Column(
        name = randomString(),
        displayName = randomString(),
        dataType = ColumnDataType.String,
        variableType = ColumnVariableType.Categorical,
        align = ColumnAlign.Left,
        statistics = Some(CategoricalStatistics(
          uniqueValuesCount = randomInt(50000),
          categoricalHistogram = CategoricalHistogram(
            Seq(
              CategoricalHistogramRow(Some(randomString()), randomInt(3000)),
              CategoricalHistogramRow(None, randomInt(3000)),
            )
          )
        ))
      )
      val continuousColumn = Column(
        name = randomString(),
        displayName = randomString(),
        dataType = ColumnDataType.Integer,
        variableType = ColumnVariableType.Continuous,
        align = ColumnAlign.Left,
        statistics = Some(NumericalStatistics(
          min = randomInt(500),
          max = randomInt(300),
          avg = randomInt(300),
          stdPopulation = randomInt(300),
          std = randomInt(300),
          mean = randomInt(300),
          numericalHistogram = NumericalHistogram(
            Seq(
              NumericalHistogramRow(randomInt(20), randomInt(20), randomInt(50))
            )
          )
        ))
      )
      val entity = TableEntity.copy(columns = Seq(categoricalColumn, continuousColumn))
      val table = WithId(entity, "id")
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      whenReady(service.getStatistics("id", None)) { result =>
        result shouldBe Right(TableStatistics(
          tableId = table.id,
          status = table.entity.tableStatisticsStatus,
          columnsStatistics = Seq(
            NamedColumnStatistics(categoricalColumn.name, categoricalColumn.statistics.get),
            NamedColumnStatistics(continuousColumn.name, continuousColumn.statistics.get)
          )
        ))
      }
    }

    "return error response when table is not found" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(None))
      whenReady(service.getStatistics("id", None))(_ shouldBe Left(TableServiceError.TableNotFound))
    }
  }

  "TableService#getTableStatisticsProcess" should {

    " return process for column statistics calculation" in {

      val process: WithId[Process] = WithId(Process(
        targetId = "id",
        targetType = AssetType.Table,
        ownerId = user.id,
        authToken = None,
        jobId = UUID.randomUUID(),
        status = ProcessStatus.Running,
        progress = None,
        estimatedTimeRemaining = None,
        created = Instant.now(),
        started = None,
        completed = None,
        errorCauseMessage = None,
        errorDetails = None,
        onComplete = ResultHandlerMeta("ColumnStatisticsResultHandler", Json.obj()),
        auxiliaryOnComplete = Seq.empty
      ), "pid")

      when(mockedProcessService.getProcess(
        any[String],
        eqTo(AssetType.Table),
        eqTo(Some(classOf[ColumnStatisticsResultHandler]))
      )).thenReturn(future(Right(process)))
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))

      whenReady(service.getTableStatisticsProcess("id"))(_ shouldBe Right(process))

    }

    "return error response when table doesn't exist" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(None))
      whenReady(service.getTableStatisticsProcess("id"))(_ shouldBe Left(TableServiceError.TableNotFound))

    }

    "return error response when process for table statistics doesn't exist" in {
      when(mockedDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))
      when(mockedProcessService.getProcess(
        any[String],
        eqTo(AssetType.Table),
        eqTo(Some(classOf[ColumnStatisticsResultHandler]))
      )).thenReturn(future(Left(ProcessNotFoundError)))
      whenReady(service.getTableStatisticsProcess("id"))(_ shouldBe Left(TableServiceError.TableStatsProcessNotFound))

    }

  }



  "TableService#list" should {

    "return tables without error" in {

      when(mockedDao.listAll(IdIn(Seq(table.id)))) thenReturn future(Seq(table))
      whenReady(service.list(table.entity.ownerId, Seq(table.id))) {
        _ shouldBe Right(List(table))
      }
    }

    "return error if user is not owner of any of the tables" in {
      when(mockedDao.listAll(IdIn(Seq(table.id)))) thenReturn future(Seq(table))
      whenReady(service.list(UUID.randomUUID(), Seq(table.id))) {
        _ shouldBe Left(InternalTableServiceError.AccessDenied)
      }
    }

    "return error if any of the table is not found" in {
      when(mockedDao.listAll(IdIn(Seq(table.id,"1")))) thenReturn future(Seq(table))
      whenReady(service.list(table.entity.ownerId, Seq(table.id,"1"))) {
        _ shouldBe Left(InternalTableServiceError.TableNotFound("1"))
      }
    }

  }

}
