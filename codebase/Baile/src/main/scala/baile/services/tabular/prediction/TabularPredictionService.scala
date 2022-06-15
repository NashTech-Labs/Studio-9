package baile.services.tabular.prediction

import java.time.Instant
import java.util.UUID

import akka.event.LoggingAdapter
import baile.dao.tabular.prediction.TabularPredictionDao
import baile.daocommons.WithId
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetType
import baile.domain.common.ClassReference
import baile.domain.dcproject.DCProjectPackage
import baile.domain.table._
import baile.domain.tabular.model.{ ModelColumn, TabularModel, TabularModelStatus }
import baile.domain.tabular.prediction.{ ColumnMapping, TabularPrediction, TabularPredictionStatus }
import baile.domain.usermanagement.User
import baile.services.asset.AssetService
import baile.services.asset.AssetService.{ WithNestedUsageTracking, WithOwnershipTransfer, WithProcess }
import baile.services.asset.AssetService.{
  AssetCreateErrors,
  WithCreate,
  WithNestedUsageTracking,
  WithProcess,
  WithSharedAccess
}
import baile.services.asset.sharing.AssetSharingService
import baile.services.common.EntityService.WithSortByField
import baile.services.cortex.job.CortexJobService
import baile.services.dcproject.DCProjectPackageService
import baile.services.process.ProcessService
import baile.services.project.ProjectService
import baile.services.table.TableService
import baile.services.tabular.model.{ TabularModelCommonService, TabularModelService }
import baile.services.tabular.prediction.TabularPredictionService.{
  TabularPredictionCreateError,
  TabularPredictionServiceError
}
import baile.services.tabular.prediction.TabularPredictionService.TabularPredictionServiceError.PredictionNotComplete
import baile.utils.TryExtensions._
import baile.utils.validation.Option._
import cats.data.EitherT
import cats.implicits._
import cortex.api.job.table.{ DataSource, ProbabilityClassColumn }
import cortex.api.job.tabular.{ PredictRequest, ColumnMapping => TabularColumnMapping }
import cortex.api.job.common.{ ClassReference => CortexClassReference }

import scala.concurrent.{ ExecutionContext, Future }

class TabularPredictionService(
  protected val tabularModelService: TabularModelService,
  protected val tableService: TableService,
  protected val tabularModelCommonService: TabularModelCommonService,
  protected val dao: TabularPredictionDao,
  protected val assetSharingService: AssetSharingService,
  protected val cortexJobService: CortexJobService,
  protected val processService: ProcessService,
  protected val projectService: ProjectService,
  protected val packageService: DCProjectPackageService
)(
  implicit val ec: ExecutionContext,
  val logger: LoggingAdapter
) extends AssetService[TabularPrediction, TabularPredictionServiceError]
  with WithSortByField[TabularPrediction, TabularPredictionServiceError]
  with WithProcess[TabularPrediction, TabularPredictionServiceError]
  with WithNestedUsageTracking[TabularPrediction, TabularPredictionServiceError]
  with WithOwnershipTransfer[TabularPrediction]
  with WithSharedAccess[TabularPrediction, TabularPredictionServiceError]
  with WithCreate[TabularPrediction, TabularPredictionServiceError, TabularPredictionCreateError] {

  import TabularPredictionService._
  import TabularPredictionCreateError._

  override val assetType: AssetType = AssetType.TabularPrediction
  override val notFoundError: TabularPredictionServiceError = TabularPredictionServiceError.NotFound
  override val forbiddenError: TabularPredictionServiceError = TabularPredictionServiceError.AccessDenied
  override val inUseError: TabularPredictionServiceError = TabularPredictionServiceError.TabularPredictionInUse
  override val sortingFieldNotFoundError: TabularPredictionServiceError =
    TabularPredictionServiceError.SortingFieldUnknown

  override protected val createErrors: AssetCreateErrors[TabularPredictionCreateError] = TabularPredictionCreateError
  override protected val findField: String => Option[Field] = Map(
    "name" -> TabularPredictionDao.Name,
    "created" -> TabularPredictionDao.Created,
    "updated" -> TabularPredictionDao.Updated
  ).get

  override def updateOwnerId(tabularPrediction: TabularPrediction, ownerId: UUID): TabularPrediction = {
    tabularPrediction.copy(ownerId = ownerId)
  }

  // scalastyle:off method.length
  def create(
    name: Option[String],
    modelId: String,
    inputTableId: String,
    columnMappings: Seq[ColumnMapping],
    description: Option[String]
  )(implicit user: User): Future[Either[TabularPredictionCreateError, WithId[TabularPrediction]]] = {

    def ensureTabularModelActive(tabularModel: WithId[TabularModel]): Either[TabularPredictionCreateError, Unit] = {
      Either.cond(
        tabularModel.entity.status == TabularModelStatus.Active,
        (),
        TabularPredictionCreateError.TabularModelNotActive
      )
    }

    def ensureColumnMappingExists: Either[TabularPredictionCreateError, Unit] = {
      Either.cond(columnMappings.nonEmpty, (), ColumnMappingNotFound)
    }

    val result = for {
      createParams <- validateAndGetAssetCreateParams(name, None)
      _ <- EitherT.fromEither[Future](ensureColumnMappingExists)
      tabularModel <- EitherT(tabularModelService.get(modelId))
        .leftMap(_ => TabularPredictionCreateError.TabularModelNotFound)
      _ <- EitherT.fromEither[Future](ensureTabularModelActive(tabularModel))
      inputTable <- EitherT(tableService.get(inputTableId))
        .leftMap(_ => TabularPredictionCreateError.TableNotFound)
      _ <- EitherT.fromEither[Future](validateColumnMappings(inputTable, tabularModel, columnMappings))
      predictionResultColumnName = tabularModelCommonService.generatePredictionResultColumnName(
        responseColumnName = tabularModel.entity.responseColumn.name,
        tableColumnsNames = inputTable.entity.columns.map(_.name)
      )
      predictionResultColumnDisplayName = tabularModelCommonService.generatePredictionResultColumnDisplayName(
        responseColumnDisplayName = tabularModel.entity.responseColumn.displayName,
        tableColumnsDisplayNames = inputTable.entity.columns.map(_.displayName)
      )
      classProbabilityColumns = tabularModelCommonService.generateProbabilityColumnsForClasses(
        tabularModel.entity,
        inputTable.entity
      )
      outputTable <- EitherT.right[TabularPredictionCreateError](createOutputTable(
        inputTable = inputTable,
        tabularModel = tabularModel.entity,
        predictionResultColumnName = predictionResultColumnName,
        predictionResultColumnDisplayName = predictionResultColumnDisplayName,
        classProbabilityColumns = Seq.empty, // TODO: restore once we add predict_proba to BaseTabularModel
        user = user
      ))
      tabularPrediction = createTabularPrediction(
        name = createParams.name,
        modelId = modelId,
        inputTableId = inputTableId,
        outputTableId = outputTable.id,
        columnMappings = columnMappings,
        description = description
      )
      tabularPredictionId <- EitherT.right[TabularPredictionCreateError](dao.create(tabularPrediction))
      cortexId <- EitherT.right[TabularPredictionCreateError](
        tabularModelCommonService.getCortexId(tabularModel).toFuture
      )
      inputDataSource <- EitherT.right[TabularPredictionCreateError](
        tableService.buildDataSource(inputTable.entity).toFuture
      )
      outputDataSource <- EitherT.right[TabularPredictionCreateError](
        tableService.buildDataSource(outputTable.entity).toFuture
      )
      packageWithId <- EitherT.right[TabularPredictionCreateError](
        packageService.get(tabularModel.entity.classReference.packageId).map(
          _.getOrElse(throw new RuntimeException("Package not found"))
        )
      )
      jobRequest = buildPredictionRequest(
        inputDataSource,
        outputDataSource,
        cortexId,
        columnMappings,
        predictionResultColumnName,
        classProbabilityColumns,
        tabularModel.entity.classReference,
        packageWithId
      )
      jobId <- EitherT.right[TabularPredictionCreateError](cortexJobService.submitJob(jobRequest, user.id))
      _ <- EitherT.right[TabularPredictionCreateError](processService.startProcess(
        jobId,
        tabularPredictionId,
        AssetType.TabularPrediction,
        classOf[TabularPredictionResultHandler],
        TabularPredictionResultHandler.Meta(tabularPredictionId, user.id),
        user.id
      ))
    } yield WithId(tabularPrediction, tabularPredictionId)
    result.value
  }

  def save(id: String)(implicit user: User): Future[Either[TabularPredictionServiceError, Unit]] = {

    def ensurePredictionComplete(
      tabularPrediction: WithId[TabularPrediction]
    ): Either[TabularPredictionServiceError, Unit] =
      if (tabularPrediction.entity.status == TabularPredictionStatus.Done){
        ().asRight
      } else {
        PredictionNotComplete.asLeft
      }

    val result = for {
      prediction <- EitherT(get(id))
      _ <- EitherT.fromEither[Future](ensurePredictionComplete(prediction))
      _ <- EitherT(tableService.update(prediction.entity.outputTableId, _.copy(inLibrary = true)))
        .leftMap[TabularPredictionServiceError](_ => TabularPredictionServiceError.TableNotFound)
    } yield ()
    result.value
  }

  def update(
    id: String,
    newName: Option[String],
    newDescription: Option[String]
  )(implicit user: User): Future[Either[TabularPredictionServiceError, WithId[TabularPrediction]]] =
    update(
      id,
      _ => newName.validate(name => validateAssetName(
        name,
        Option(id),
        TabularPredictionServiceError.PredictionNameCanNotBeEmpty,
        TabularPredictionServiceError.TabularPredictionNameAlreadyExists(name)
      )),
      prediction => prediction.copy(
        name = newName.getOrElse(prediction.name),
        description = newDescription orElse prediction.description
      )
    )

  override protected def preDelete(
    asset: WithId[TabularPrediction]
  )(implicit user: User): Future[Either[TabularPredictionServiceError, Unit]] = {
    def dropOutputTable(): Future[Either[TabularPredictionServiceError, Unit]] = {
      val tableId = asset.entity.outputTableId

      def loadTable(): Future[WithId[Table]] = tableService.get(tableId).map(_.valueOr {
        error => throw new RuntimeException(s"Unexpected error received while getting album $tableId : $error")
      })

      def deleteIfNotInLibrary(table: WithId[Table]): Future[Unit] =
        if (table.entity.inLibrary) Future.successful(())
        else tableService.delete(table.id).map(_ => ())

      for {
        table <- loadTable()
        result <- deleteIfNotInLibrary(table)
      } yield result.asRight[TabularPredictionServiceError]
    }

    val result = for {
      _ <- EitherT(super.preDelete(asset))
      _ <- EitherT(dropOutputTable())
    } yield ()

    result.value
  }

  private def createTabularPrediction(
    name: String,
    modelId: String,
    inputTableId: String,
    outputTableId: String,
    columnMappings: Seq[ColumnMapping],
    description: Option[String]
  )(implicit user: User): TabularPrediction = {
    val dateTime = Instant.now

    TabularPrediction(
      ownerId = user.id,
      name = name,
      status = TabularPredictionStatus.Running,
      created = dateTime,
      updated = dateTime,
      modelId = modelId,
      inputTableId = inputTableId,
      outputTableId = outputTableId,
      columnMappings = columnMappings,
      description = description
    )
  }

  private def validateColumnMappings(
    inputTable: WithId[Table],
    model: WithId[TabularModel],
    columnMappings: Seq[ColumnMapping]
  ): Either[TabularPredictionCreateError, Unit] = {

    def ensureModelPredictorIsMapped(predictor: ModelColumn): Either[TabularPredictionCreateError, Unit] = {

      def ensureMappingIsProvided(): Either[TabularPredictionCreateError, ColumnMapping] =
        columnMappings.find(_.trainName == predictor.name) match {
          case Some(columnMapping) => columnMapping.asRight
          case None => PredictorColumnMissing(predictor).asLeft
        }

      def ensureTableContainsPredictor(columnMapping: ColumnMapping): Either[TabularPredictionCreateError, Column] =
        inputTable.entity.columns.find(_.name == columnMapping.currentName) match {
          case Some(column) => column.asRight
          case None => PredictorNotFoundInTable(columnMapping, inputTable.entity).asLeft
        }

      def ensureColumnTypeIsValid(column: Column): Either[TabularPredictionCreateError, Unit] = {
        val allowedTypes = tableService.allowedTypeConversions(column.dataType)
        if (allowedTypes.contains(predictor.dataType)) {
          ().asRight
        } else{
          InvalidColumnDataType(predictor, allowedTypes).asLeft
        }
      }

      for {
        mapping <- ensureMappingIsProvided()
        predictorColumn <- ensureTableContainsPredictor(mapping)
        _ <- ensureColumnTypeIsValid(predictorColumn)
      } yield ()

    }

    def ensureColumnMappingIsUnique: Either[TabularPredictionCreateError, Unit] = {
      val currentNames = columnMappings.map(_.currentName)
      Either.cond(currentNames.distinct.size == currentNames.size, (), ColumnMappingNotUnique)
    }

    type EitherErrorOr[R] = Either[TabularPredictionCreateError, R]

    for {
      _ <- ensureColumnMappingIsUnique
      _ <- model.entity.predictorColumns
        .toList
        .map(ensureModelPredictorIsMapped)
        .sequence[EitherErrorOr, Unit]
        .map(_ => ())
      _ <- Either.cond(
        model.entity.predictorColumns.length === columnMappings.length,
        (),
        ColumnMappingWithInvalidPredictor
      )
    } yield ()
  }

  private def buildPredictionRequest(
    inputDataSource: DataSource,
    outputDataSource: DataSource,
    cortexId: String,
    columnMappings: Seq[ColumnMapping],
    predictionResultColumnName: String,
    probabilityColumnsValues: Seq[(String, Column)],
    classReference: ClassReference,
    packageWithId: WithId[DCProjectPackage]
  ): PredictRequest = {
    val probabilityColumns = probabilityColumnsValues map {
      case (className, column) => ProbabilityClassColumn(className, column.name)
    }
    PredictRequest(
      modelId = cortexId,
      input = Some(inputDataSource),
      output = Some(outputDataSource),
      predictors = columnMappings.map(pair => TabularColumnMapping(pair.trainName, pair.currentName)),
      predictionResultColumnName = predictionResultColumnName,
      probabilityColumns = probabilityColumns,
      modelReference = Some(CortexClassReference(
        packageLocation = packageWithId.entity.location,
        className = classReference.className,
        moduleName = classReference.moduleName
      ))
    )
  }

  private def createOutputTable(
    inputTable: WithId[Table],
    tabularModel: TabularModel,
    predictionResultColumnName: String,
    predictionResultColumnDisplayName: String,
    classProbabilityColumns: Seq[Column],
    user: User
  ): Future[WithId[Table]] = {
    val modelResponseColumn = tabularModel.responseColumn.copy(
      name = predictionResultColumnName,
      displayName = predictionResultColumnDisplayName
    )
    val predictionResultColumn = Column(
      name = modelResponseColumn.name,
      displayName = modelResponseColumn.displayName,
      dataType = modelResponseColumn.dataType,
      variableType = modelResponseColumn.variableType,
      align = tableService.getColumnAlignment(modelResponseColumn.dataType),
      statistics = None
    )
    val additionalColumns = predictionResultColumn +: classProbabilityColumns
    tableService.createEmptyTable(
      name = None,
      tableType = TableType.Derived,
      columns = additionalColumns ++ inputTable.entity.columns,
      inLibrary = false,
      user = user
    )
  }

}

object TabularPredictionService {

  sealed trait TabularPredictionCreateError

  object TabularPredictionCreateError extends AssetCreateErrors[TabularPredictionCreateError] {

    case object PredictionNameCanNotBeEmpty extends TabularPredictionCreateError

    case object TabularModelNotFound extends TabularPredictionCreateError

    case object TableNotFound extends TabularPredictionCreateError

    case object ColumnMappingNotFound extends TabularPredictionCreateError

    case class InvalidColumnDataType(
      column: ModelColumn,
      allowedTypes: Set[ColumnDataType]
    ) extends TabularPredictionCreateError

    case object TabularModelNotActive extends TabularPredictionCreateError

    case class TabularPredictionNameAlreadyExists(name: String) extends TabularPredictionCreateError

    case object NameNotSpecified extends TabularPredictionCreateError

    case class PredictorNotFoundInTable(predictor: ColumnMapping, table: Table) extends TabularPredictionCreateError

    case class PredictorColumnMissing(column: ModelColumn) extends TabularPredictionCreateError

    case object ColumnMappingWithInvalidPredictor extends TabularPredictionCreateError

    case object ColumnMappingNotUnique extends TabularPredictionCreateError

    override val nameNotSpecifiedError: TabularPredictionCreateError = NameNotSpecified
    override val emptyNameError: TabularPredictionCreateError = PredictionNameCanNotBeEmpty

    override def nameAlreadyExistsError(name: String): TabularPredictionCreateError =
      TabularPredictionNameAlreadyExists(name)
  }

  sealed trait TabularPredictionServiceError

  object TabularPredictionServiceError {

    case object NotFound extends TabularPredictionServiceError

    case object SortingFieldUnknown extends TabularPredictionServiceError

    case object AccessDenied extends TabularPredictionServiceError

    case object TableNotFound extends TabularPredictionServiceError

    case class TabularPredictionNameAlreadyExists(name: String) extends TabularPredictionServiceError

    case object PredictionNameCanNotBeEmpty extends TabularPredictionServiceError

    case object PredictionNotComplete extends TabularPredictionServiceError

    case object TabularPredictionInUse extends TabularPredictionServiceError

  }

}
