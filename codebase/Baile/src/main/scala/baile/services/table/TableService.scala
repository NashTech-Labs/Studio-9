package baile.services.table

import java.time.Instant
import java.util.UUID

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.http.caching.scaladsl.Cache
import akka.stream.scaladsl.Source
import baile.dao.table.SQLTableDataDao.{ EqualTo, ILike }
import baile.dao.table.{ TableDao, TableDataDao }
import baile.daocommons.WithId
import baile.daocommons.filters.{ FalseFilter, Filter, IdIn }
import baile.daocommons.sorting.{ Direction, Field, SortBy }
import baile.domain.asset.AssetType
import baile.domain.process.Process
import baile.domain.table._
import baile.domain.usermanagement.User
import baile.services.asset.AssetService
import baile.services.asset.AssetService.{
  WithOwnershipTransfer,
  AssetCreateErrors,
  WithCreate,
  WithNestedUsageTracking,
  WithProcess,
  WithSharedAccess
}
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.EntityService.WithSortByField
import baile.services.common.EntityUpdateFailedException
import baile.services.cortex.job.CortexJobService
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.table.TableService._
import baile.utils.TryExtensions._
import baile.utils.validation.Option._
import cats.data.EitherT
import cats.implicits._
import com.typesafe.config.Config
import cortex.api.job.table.{
  DataSource,
  TableMeta,
  TableUploadRequest,
  TabularColumnStatisticsRequest,
  VariableTypeInfo,
  Column => CortexTableColumn,
  ColumnInfo => CortexColumnInfo,
  DataType => CortexDataType,
  FileType => CortexFileType,
  VariableType => CortexVariableType
}

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class TableService(
  conf: Config,
  tableDataDao: TableDataDao,
  protected val assetSharingService: AssetSharingService,
  rowsCountCache: Cache[String, Int],
  protected val dao: TableDao,
  protected val processService: ProcessService,
  protected val cortexJobService: CortexJobService,
  protected val projectService: ProjectService
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends AssetService[Table, TableServiceError]
  with WithSortByField[Table, TableServiceError]
  with WithProcess[Table, TableServiceError]
  with WithSharedAccess[Table, TableServiceError]
  with WithNestedUsageTracking[Table, TableServiceError]
  with WithOwnershipTransfer[Table]
  with WithCreate[Table, TableServiceError, TableServiceError] {

  import TableServiceError._

  override val forbiddenError: TableServiceError = AccessDenied
  override val sortingFieldNotFoundError: TableServiceError = SortingFieldUnknown

  override protected val createErrors: AssetCreateErrors[TableServiceError] = TableServiceError
  override protected val findField: String => Option[Field] = Map(
    "name" -> TableDao.Name,
    "created" -> TableDao.Created,
    "updated" -> TableDao.Updated
  ).get
  override val notFoundError: TableServiceError = TableNotFound
  override val assetType: AssetType = AssetType.Table
  override val inUseError: TableServiceError = TableInUse

  override def updateOwnerId(table: Table, ownerId: UUID): Table = table.copy(ownerId = ownerId)

  def cloneTable(
    newName: String,
    tableId: String,
    sharedResourceId: Option[String] = None
  )(implicit user: User): Future[Either[TableServiceError, WithId[Table]]] = {

    val result = for {
      table <- EitherT(get(tableId, sharedResourceId))
      _ <- EitherT.fromEither[Future](ensureTableActive(table.entity.status))
      _ <- EitherT(validateAssetName(newName, None, TableNameIsEmpty, TableNameIsNotUnique(newName)))
      dateTime = Instant.now()
      cloneTable <- EitherT.right[TableServiceError](dao.create(id => table.entity.copy(
        ownerId = user.id,
        name = newName,
        status = TableStatus.Active,
        updated = dateTime,
        created = dateTime
      )))
    } yield cloneTable
    // TODO: Update Stats after/while implementing COR-1034
    result.value
  }

  // scalastyle:off method.length
  def updateTable(
    tableId: String,
    newName: Option[String],
    newDescription: Option[String],
    updateColumnsParams: Seq[UpdateColumnParams]
  )(implicit user: User): Future[Either[TableServiceError, WithId[Table]]] = {

    type EitherE[R] = Either[TableServiceError, R]

    def validateColumns(
      oldColumns: Seq[Column]
    ): Either[TableServiceError, Unit] =
      updateColumnsParams.map { update =>
        oldColumns.find(_.name == update.name) match {
          case Some(column) =>
            val updatedColumn = column.copy(
              displayName = update.displayName.getOrElse(column.displayName),
              variableType = update.variableType.getOrElse(column.variableType),
              align = update.align.getOrElse(column.align)
            )
            if (updatedColumn.variableType == ColumnVariableType.Continuous) {
              ensureValidContinuousDataType(updatedColumn)
            } else {
              ().asRight
            }
          case None => TableDoesNotHaveSuchColumn.asLeft
        }
      }.toList.sequence[EitherE, Unit].map(_ => ())

    def getColumnsWithUpdatedVariableType(
      oldColumns: Seq[Column],
      updatedColumns: Seq[Column]
    ): Seq[Column] = {
      updatedColumns.filter { updatedColumn =>
        oldColumns.exists { column =>
          column.name == updatedColumn.name && updatedColumn.variableType != column.variableType
        }
      }
    }

    def updateColumns(
      oldColumns: Seq[Column]
    ): Seq[Column] =
      oldColumns.map { column =>
        updateColumnsParams.find(_.name == column.name) match {
          case Some(columnRequest) =>
            column.copy(
              displayName = columnRequest.displayName.getOrElse(column.displayName),
              variableType = columnRequest.variableType.getOrElse(column.variableType),
              align = columnRequest.align.getOrElse(column.align)
            )
          case None => column
        }
      }

    def ensureValidContinuousDataType(column: Column): Either[TableServiceError, Unit] = {
      val validContinuousDataTypes = Seq(ColumnDataType.Integer, ColumnDataType.Long, ColumnDataType.Double)
      Either.cond(
        validContinuousDataTypes.contains(column.dataType),
        (),
        InvalidContinuousDataType
      )
    }

    def ensureCanUpdateColumns(table: WithId[Table]): Future[Either[TableServiceError, Unit]] = {
      if (updateColumnsParams.isEmpty) Future.successful(().asRight)
      else ensureCanUpdateContent(table, user).map(_.leftMap(_ => CantUpdateTable))
    }

    def processCalculationsForColumnStatistics(oldColumns: Seq[Column], updatedColumns: Seq[Column]): Future[Unit] = {
      val columns = getColumnsWithUpdatedVariableType(oldColumns, updatedColumns)
      if (columns.isEmpty) {
        Future.successful(())
      } else {
        calculateColumnStatistics(tableId, Some(columns), user.id)
      }
    }

    val result = for {
      table <- EitherT(get(tableId))
      _ <- EitherT(ensureCanUpdateColumns(table))
      oldColumns = table.entity.columns
      updatedColumns = updateColumns(oldColumns)
      updatedTable <- EitherT(this.update(tableId, table => {
        val result = for {
          _ <- EitherT.fromEither[Future](ensureTableActive(table.entity.status))
          _ <- EitherT.fromEither[Future](validateColumns(table.entity.columns))
          _ <- EitherT(newName.validate(name => validateAssetName[TableServiceError](
            name,
            Option(tableId),
            TableServiceError.TableNameIsEmpty,
            TableServiceError.TableNameIsNotUnique(name)
          )))
        } yield ()

        result.value
      }, table => table.copy(
        name = newName.getOrElse(table.name),
        columns = updatedColumns,
        description = newDescription orElse table.description
      )))
      _ <- EitherT.right[TableServiceError](processCalculationsForColumnStatistics(oldColumns, updatedColumns))
    } yield updatedTable

    result.value
  }

  def uploadTable(
    params: Map[String, String],
    fileType: FileType,
    tableFilePath: String,
    delimiter: String,
    nullValue: Option[String],
    description: Option[String],
    columns: Option[Seq[ColumnInfo]]
  )(implicit user: User): Future[Either[TableServiceError, WithId[Table]]] = {

    import cortex.api.job.table.DataSource

    def validateColumns(): Either[TableServiceError, Unit] =
      Either.cond(
        columns.forall { cols =>
          cols.map(_.name).distinct.length == cols.length &&
            cols.map(c => c.displayName.getOrElse(c.name)).distinct.length == cols.length
        },
        (),
        TableColumnNamesNotUnique
      )

    def buildRequest(dataSource: DataSource): Try[TableUploadRequest] = Try {
      TableUploadRequest(
        dataSource = Some(dataSource),
        sourceFilePath = tableFilePath,
        delimeter = delimiter,
        nullValue = nullValue.getOrElse(""),
        fileType = fileType match {
          case FileType.CSV => CortexFileType.CSV
          case FileType.JSON => CortexFileType.JSON
        },
        columns = columns match {
          case Some(columnInformation) => columnInformation.map(value => columnInfoToCortexColumnInfo(value))
          case None => Seq.empty[CortexColumnInfo]
        }
      )
    }

    def columnInfoToCortexColumnInfo(columnData: ColumnInfo): CortexColumnInfo =
      CortexColumnInfo(
        name = columnData.name,
        displayName = columnData.displayName,
        variableType = columnData.variableType.map(
          value => VariableTypeInfo(variableTypeToCortexVariableType(value))
        ),
        datatype = dataTypeToCortexDataType(columnData.dataType)
      )

    val result = for {
      inLibrary <- EitherT.fromEither[Future] {
        params.get("inLibrary") match {
          case None => None.asRight
          case Some(str) =>
            Try(str.toBoolean) match {
              case Failure(_) => InLibraryWrongFormat.asLeft
              case Success(value) => Some(value).asRight
            }
        }
      }
      _ <- EitherT.fromEither[Future](validateColumns())
      createParams <- validateAndGetAssetCreateParams(params.get("name"), inLibrary)
      table <- EitherT.right(createEmptyTable(
        name = Some(createParams.name),
        tableType = TableType.Source,
        columns = Seq.empty,
        inLibrary = createParams.inLibrary,
        user = user,
        description = description
      ))
      dataSource <- EitherT.right[TableServiceError](buildDataSource(table.entity).toFuture)
      jobMessage <- EitherT.right[TableServiceError](buildRequest(dataSource).toFuture)
      uploadJobId <- EitherT.right[TableServiceError](cortexJobService.submitJob(jobMessage, user.id))
      _ <- EitherT.right[TableServiceError](processService.startProcess(
        jobId = uploadJobId,
        targetId = table.id,
        targetType = AssetType.Table,
        handlerClass = classOf[TableUploadResultHandler],
        meta = TableUploadResultHandler.Meta(table.id, tableFilePath, user.id),
        userId = user.id
      ))
    } yield table
    result.value
  }

  def getColumnValues(
    id: String,
    columnName: String,
    searchParam: Option[String],
    limit: Int,
    sharedResourceId: Option[String]
  )(implicit user: User): Future[Either[TableServiceError, Seq[TableRowValue]]] = {

    def getColumnInfo(table: WithId[Table]): Either[TableServiceError, Column] = {
      table.entity.columns.find(_.name == columnName) match {
        case Some(column) => column.asRight
        case None => ColumnNotFound(columnName).asLeft
      }
    }

    def prepareFilter(columnInfo: Column): Either[TableServiceError, Option[Filter]] = {
      searchParam match {
        case Some(search) =>
          columnInfo.dataType match {
            case ColumnDataType.Boolean if List("true", "false").contains(search.toLowerCase) =>
              Some(EqualTo(columnInfo, search.toLowerCase)).asRight
            case ColumnDataType.Boolean => InvalidSearchParams.asLeft
            case _ => Some(ILike(columnInfo, search.toLowerCase)).asRight
          }
        case None => None.asRight
      }
    }

    def boundLimit: Try[Int] = Try {
      Math.min(limit, conf.getInt("tabular-storage.max-preview-count"))
    }

    def getResult(table: WithId[Table], filter: Option[Filter], boundedLimit: Int): Future[Seq[TableRowValue]] = {
      tableDataDao.getColumnValues(
        table = table.entity,
        columnName = columnName,
        limit = boundedLimit,
        search = filter
      )
    }

    val result = for {
      table <- EitherT(get(id, sharedResourceId))
      _ <- EitherT.fromEither[Future](ensureTableActive(table.entity.status))
      columnInfo <- EitherT.fromEither[Future](getColumnInfo(table))
      filter <- EitherT.fromEither[Future](prepareFilter(columnInfo))
      boundedLimit <- EitherT.right(boundLimit.toFuture)
      columnValues <- EitherT.right[TableServiceError](getResult(table, filter, boundedLimit))
    } yield columnValues

    result.value
  }

  def getTableData(
    id: String,
    pageSize: Int,
    pageNumber: Int,
    search: Option[String],
    orderBy: Option[String],
    sharedResourceId: Option[String]
  )(implicit user: User): Future[Either[TableServiceError, (Seq[TableRow], Long)]] = {

    def buildSortBy(table: Table): Either[TableServiceError, Option[SortBy]] = {

      def getOrderByParam(param: String): Either[TableServiceError, Option[SortBy]] = {

        type EitherType[R] = Either[TableServiceError, R]

        val sortParams: EitherType[List[(Column, Direction)]] =
          param.split(",").toList.traverse[EitherType, (Column, Direction)] {
            case "" | "-" => EmptySortingKey.asLeft
            case term =>
              val (sortingKey, direction) = term.head match {
                case '-' => (term.tail, Direction.Descending)
                case _ => (term, Direction.Ascending)
              }
              table.columns.find(_.name == sortingKey) match {
                case Some(column) => (column, direction).asRight
                case None => ColumnNotFound(sortingKey).asLeft
              }
          }
        sortParams.map {
          case Nil => None
          case value => Some(SortBy(value: _*))
        }
      }

      orderBy match {
        case None => None.asRight
        case Some(sortParam) => getOrderByParam(sortParam)
      }
    }

    def buildSearchFilter(table: Table): Option[Filter] = {
      search.map { searchParam =>
        table.columns.foldLeft[Filter](FalseFilter) {
          (filter, column) => column.dataType match {
            case ColumnDataType.Boolean =>
              if (List("true", "false").contains(searchParam.toLowerCase)) {
                filter || EqualTo(column, searchParam.toLowerCase)
              } else {
                filter
              }
            case _ =>
              filter || ILike(column, searchParam.toLowerCase)
          }
        }
      }
    }

    val result = for {
      table <- EitherT(get(id, sharedResourceId))
      _ <- EitherT.fromEither[Future](ensureTableActive(table.entity.status))
      sortParam <- EitherT.fromEither[Future](buildSortBy(table.entity))
      searchFilter = buildSearchFilter(table.entity)
      rows <- EitherT.right[TableServiceError](
        tableDataDao.getRows(table.entity, pageSize, pageNumber, searchFilter, sortParam)
      )
      count <- EitherT.right[TableServiceError](tableDataDao.getRowsCount(table.entity, searchFilter))
    } yield (rows, count)
    result.value
  }

  def getStatistics(
    id: String,
    sharedResourceId: Option[String]
  )(implicit user: User): Future[Either[TableServiceError, TableStatistics]] =
    get(id, sharedResourceId).map(_.map { table =>
    TableStatistics(
      tableId = table.id,
      status = table.entity.tableStatisticsStatus,
      columnsStatistics =
        for {
          column <- table.entity.columns
          statistics <- column.statistics
        } yield NamedColumnStatistics(column.name, statistics)
    )
  })

  def getTableStatisticsProcess(
    id: String
  )(implicit user: User): Future[Either[TableServiceError, WithId[Process]]] = {
    val result = for {
      _ <- EitherT(get(id)).leftMap[TableServiceError](_ => TableServiceError.TableNotFound)
      process <- EitherT(processService.getProcess(
        targetId = id,
        targetType = AssetType.Table,
        handlerClass = Some(classOf[ColumnStatisticsResultHandler])
      )).leftMap[TableServiceError](_ => TableStatsProcessNotFound)
    } yield process

    result.value
  }

  def export(
    id: String,
    sharedResourceId: Option[String]
  )(implicit user: User): Future[Either[TableServiceError, ExportResult]] = {
    val result = for {
      table <- EitherT(get(id, sharedResourceId))
      source <- EitherT.right[TableServiceError](tableDataDao.getTableRowSource(table.entity))
    } yield ExportResult(table, source)

    result.value
  }

  def save(id: String, name: String)(implicit user: User): Future[Either[TableServiceError, WithId[Table]]] = {
    val result = for {
      _ <- EitherT(validateAssetName(name, Option(id), TableNameIsEmpty, TableNameIsNotUnique(name)))
      table <- EitherT(this.update(id, _.copy(name = name, inLibrary = true)))
    } yield table
    result.value
  }

  def list(
    userId: UUID,
    tableIds: Seq[String]
  ): Future[Either[InternalTableServiceError, List[WithId[Table]]]] = {

    def checkAccessGranted(tables: WithId[Table]): Either[InternalTableServiceError, Unit] = {
      if (tables.entity.ownerId == userId) ().asRight
      else InternalTableServiceError.AccessDenied.asLeft
    }

    def fetchTable(
      tables: Seq[WithId[Table]],
      tableId: String
    ): Either[InternalTableServiceError, WithId[Table]] = {
      tables.find(_.id == tableId) match {
        case Some(table) => table.asRight
        case None =>  InternalTableServiceError.TableNotFound(tableId).asLeft
      }
    }

    def validateAndFetchTable(
      tableId: String,
      tables: Seq[WithId[Table]]
    ): Either[InternalTableServiceError, WithId[Table]] ={
      for {
        table <- fetchTable(tables, tableId)
        _ <- checkAccessGranted(table)
      } yield table
    }

    type CreateErrorOr[R] = Either[InternalTableServiceError, R]

    val result = for {
      tables <- EitherT.right[InternalTableServiceError](dao.listAll(IdIn(tableIds)))
      tablesResult <- EitherT.fromEither[Future](
        tableIds.map(validateAndFetchTable(_, tables)).toList.sequence[CreateErrorOr, WithId[Table]]
      )
    } yield tablesResult

    result.value

  }

  override protected def preDelete(
    entity: WithId[Table]
  )(implicit user: User): Future[Either[TableServiceError, Unit]] = {
    super.preDelete(entity).map { _ =>
      rowsCountCache.remove(entity.id)
      ().asRight
    }
  }

  private[services] def calculateColumnStatistics(
    tableId: String,
    columns: Option[Seq[Column]],
    userId: UUID
  ): Future[Unit] = {

    def columnToCortexColumn(column: Column): CortexTableColumn =
      CortexTableColumn(
        name = column.name,
        displayName = column.displayName,
        datatype = dataTypeToCortexDataType(column.dataType),
        variableType = variableTypeToCortexVariableType(column.variableType)
      )

    for {
      table <- loadTableMandatory(tableId)
      dataSource <- buildDataSource(table.entity).toFuture
      columnsForCalculation = columns.getOrElse(table.entity.columns)
      columnStatisticsRequest = TabularColumnStatisticsRequest(
        dataSource = Some(dataSource),
        columns = columnsForCalculation.map(columnToCortexColumn),
        histogramLength = conf.getInt("tables.histogram-length")
      )
      calculateJobId <- cortexJobService.submitJob(columnStatisticsRequest, userId)
      _ <- dao.update(
        tableId, _.copy(tableStatisticsStatus = TableStatisticsStatus.Pending)
      )
      _ <- processService.startProcess(
        jobId = calculateJobId,
        targetId = tableId,
        targetType = AssetType.Table,
        handlerClass = classOf[ColumnStatisticsResultHandler],
        meta = ColumnStatisticsResultHandler.Meta(tableId, userId),
        userId = userId
      )
    } yield ()
  }

  private[services] def loadTableMandatory(tableId: String): Future[WithId[Table]] = {
    dao.get(tableId).map(_.getOrElse(throw new RuntimeException(
      s"Unexpectedly not found table $tableId in storage"
    )))
  }

  private[services] def buildDataSource(table: Table): Try[DataSource] = Try {

    import cortex.api.job.table.{ DataSource, Table, TableColumn => CortexColumn }

    def columnToCortexColumn(column: Column): CortexColumn =
      CortexColumn(
        name = column.name,
        dataType = dataTypeToCortexDataType(column.dataType),
        variableType = variableTypeToCortexVariableType(column.variableType)
      )

    DataSource(
      table = Some(Table(
        meta = Some(buildTableMeta(table)),
        columns = table.columns.map(columnToCortexColumn)
      ))
    )
  }

  private[services] def buildTableMeta(table: Table): TableMeta =
    TableMeta(
      schema = table.repositoryId,
      name = table.databaseId
    )

  private[services] def dataTypeToCortexDataType(dataType: ColumnDataType): CortexDataType =
    dataType match {
      case ColumnDataType.String => CortexDataType.STRING
      case ColumnDataType.Integer => CortexDataType.INTEGER
      case ColumnDataType.Boolean => CortexDataType.BOOLEAN
      case ColumnDataType.Double => CortexDataType.DOUBLE
      case ColumnDataType.Long => CortexDataType.LONG
      case ColumnDataType.Timestamp => CortexDataType.TIMESTAMP
    }

  private[services] def variableTypeToCortexVariableType(variableType: ColumnVariableType): CortexVariableType =
    variableType match {
      case ColumnVariableType.Continuous => CortexVariableType.CONTINUOUS
      case ColumnVariableType.Categorical => CortexVariableType.CATEGORICAL
    }

  private def ensureTableActive(tableStatus: TableStatus): Either[TableServiceError, Unit] = {
    if (tableStatus == TableStatus.Active) ().asRight
    else TableIsNotActive.asLeft
  }

  private[services] def normalizeName(name: String): String = name.toLowerCase.replaceAll("[^a-z0-9]", "_")

  private[services] def createEmptyTable(
    name: Option[String],
    tableType: TableType,
    columns: Seq[Column],
    inLibrary: Boolean,
    user: User,
    description: Option[String] = None
  ): Future[WithId[Table]] = {

    def getDefaultRepoId(user: User): String = normalizeName(user.username + "_" + user.id + "_default")

    def generateDatabaseId(id: String, name: String) = s"ds_${ id }_${ normalizeName(name) }"

    val now = Instant.now()

    dao.create { id =>
      val tableName = name getOrElse s"TABLE $id"
      Table(
        name = tableName,
        ownerId = user.id,
        databaseId = generateDatabaseId(id, tableName),
        repositoryId = getDefaultRepoId(user),
        columns = columns,
        created = now,
        updated = now,
        status = TableStatus.Saving,
        `type` = tableType,
        inLibrary = inLibrary,
        tableStatisticsStatus = TableStatisticsStatus.Pending,
        description = description
      )
    }
  }

  private[services] def getColumnAlignment(columnDataType: ColumnDataType): ColumnAlign = columnDataType match {
    case ColumnDataType.Boolean | ColumnDataType.String | ColumnDataType.Timestamp => ColumnAlign.Left
    case ColumnDataType.Double | ColumnDataType.Integer | ColumnDataType.Long => ColumnAlign.Right
  }

  private[services] def updateStatus(id: String, status: TableStatus): Future[Unit] =
    dao.update(id, _.copy(status = status)).map(
      _.getOrElse(throw EntityUpdateFailedException(id, classOf[Table]))
    )

  private[services] def allowedTypeConversions(dataType: ColumnDataType): Set[ColumnDataType] = {
    import ColumnDataType._
    val additionalConversions = dataType match {
      case String => List.empty
      case Integer => List(Double, Long)
      case Boolean => List.empty
      case Double => List.empty
      case Long => List(Double)
      case Timestamp => List.empty
    }
    (String :: dataType :: additionalConversions).toSet
  }

  private[services] def updateTable(tableId: String, updater: Table => Table): Future[WithId[Table]] =
    dao.update(tableId, updater).map(_.getOrElse(throw EntityUpdateFailedException(tableId, classOf[Table])))

}

object TableService {

  sealed trait TableServiceError

  object TableServiceError extends AssetCreateErrors[TableServiceError] {

    case class TableNameIsNotUnique(name: String) extends TableServiceError

    case object TableIsNotActive extends TableServiceError

    case object TableNameIsEmpty extends TableServiceError

    case object NameNotSpecified extends TableServiceError

    case object TableNotFound extends TableServiceError

    case object AccessDenied extends TableServiceError

    case object SortingFieldUnknown extends TableServiceError

    case object InvalidContinuousDataType extends TableServiceError

    case object TableDoesNotHaveSuchColumn extends TableServiceError

    case class ColumnNotFound(columnName: String) extends TableServiceError

    case object EmptySortingKey extends TableServiceError

    case object InvalidSearchParams extends TableServiceError

    case object TableInUse extends TableServiceError

    case object CantUpdateTable extends TableServiceError

    case object TableStatsProcessNotFound extends TableServiceError

    case object InLibraryWrongFormat extends TableServiceError

    case object TableColumnNamesNotUnique extends TableServiceError

    override val nameNotSpecifiedError: TableServiceError = NameNotSpecified
    override val emptyNameError: TableServiceError = TableNameIsEmpty

    override def nameAlreadyExistsError(name: String): TableNameIsNotUnique = TableNameIsNotUnique(name)
  }

  sealed trait InternalTableServiceError

  object InternalTableServiceError {

    case class TableNotFound(tableId: String) extends InternalTableServiceError

    case object AccessDenied extends InternalTableServiceError

  }

  case class UpdateColumnParams(
    name: String,
    displayName: Option[String],
    variableType: Option[ColumnVariableType],
    align: Option[ColumnAlign]
  )

  case class ColumnInfo(
    name: String,
    displayName: Option[String],
    variableType: Option[ColumnVariableType],
    dataType: ColumnDataType
  )

  case class NamedColumnStatistics(
    columnName: String,
    columnsStatistics: ColumnStatistics
  )

  case class TableStatistics(
    tableId: String,
    status: TableStatisticsStatus,
    columnsStatistics: Seq[NamedColumnStatistics]
  )

  case class ExportResult(
    table: WithId[Table],
    data: Source[TableRow, NotUsed]
  )

}
