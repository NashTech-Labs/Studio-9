package baile.services.tabular.model

import java.time.Instant
import java.util.UUID

import baile.dao.asset.Filters.{ NameIs, OwnerIdIs }
import baile.dao.table.TableDao
import baile.dao.tabular.model.TabularModelDao
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.common.ClassReference
import baile.domain.experiment.ExperimentStatus
import baile.domain.table._
import baile.domain.tabular.model.{ ModelColumn, TabularModel, TabularModelStatus }
import baile.domain.tabular.pipeline.TabularTrainPipeline
import baile.domain.tabular.result.TabularTrainResult
import baile.domain.usermanagement.User
import baile.services.cortex.job.CortexJobService
import baile.services.dcproject.DCProjectPackageService
import baile.services.experiment.PipelineHandler
import baile.services.experiment.PipelineHandler.{ CreateError, PipelineCreatedResult }
import baile.services.process.ProcessService
import baile.services.table.TableService
import baile.services.tabular.model.TabularTrainPipelineHandler.TabularModelCreateError._
import baile.services.tabular.model.TabularTrainPipelineHandler.{
  EvaluationParams,
  NonSuccessfulTerminalStatus,
  Params,
  TabularModelCreateError,
  TypedEvaluationParams
}
import baile.utils.TryExtensions._
import baile.utils.UniqueNameGenerator
import cats.data.EitherT
import cats.implicits._
import com.typesafe.config.Config
import cortex.api.job.common.{ ClassReference => CortexClassReference }
import cortex.api.job.table.{
  ProbabilityClassColumn,
  TableColumn => CortexColumn
}
import cortex.api.job.tabular.{
  ColumnMapping,
  EvaluateRequest,
  TrainRequest
}

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class TabularTrainPipelineHandler(
  modelDao: TabularModelDao,
  tableDao: TableDao,
  modelService: TabularModelService,
  tableService: TableService,
  tabularModelCommonService: TabularModelCommonService,
  cortexJobService: CortexJobService,
  processService: ProcessService,
  packageService: DCProjectPackageService,
  conf: Config
)(implicit val ec: ExecutionContext) extends PipelineHandler[
  TabularTrainPipeline,
  Params,
  TabularTrainResult,
  TabularModelCreateError
] {

  type CreateErrorOr[R] = Either[TabularModelCreateError, R]
  type FutureCreateErrorOr[R] = Future[CreateErrorOr[R]]

  private val modelPackageName = conf.getString("package-name")
  private val modelModuleName = conf.getString("module-name")
  private val modelClassName = conf.getString("class-name")

  override protected def validatePipelineAndLoadParams(
    pipeline: TabularTrainPipeline
  )(implicit user: User): Future[Either[TabularModelCreateError, Params]] = {

    def loadInputTable(id: String): FutureCreateErrorOr[WithId[Table]] =
      tableService.get(id).map(_.leftMap(_ => TableNotFound(id)))

    def loadOptionalInputTable(tableId: Option[String]): FutureCreateErrorOr[Option[WithId[Table]]] =
      tableId match {
        case Some(id) =>
          loadInputTable(id).map(_.map(Some(_)))
        case None =>
          Future.successful(None.asRight)
      }

    def validateTablesStatuses(tables: List[WithId[Table]]): CreateErrorOr[Unit] =
      tables.map { table =>
        Either.cond(
          table.entity.status == TableStatus.Active,
          (),
          TableNotActive(table.id)
        )
      }.sequence[CreateErrorOr, Unit].map(_ => ())

    def validateColumnsInTables(tables: List[WithId[Table]], weightColumn: Option[ModelColumn]): CreateErrorOr[Unit] = {
      val allColumns = (pipeline.responseColumn :: pipeline.predictorColumns) ::: weightColumn.toList
      tables
        .map(validateColumnsInTable(allColumns, _))
        .sequence[CreateErrorOr, Unit].map(_ => ())
    }

    val result = for {
      inputTable <- EitherT(loadInputTable(pipeline.inputTableId))
      holdOutInputTable <- EitherT(loadOptionalInputTable(pipeline.holdOutInputTableId))
      outOfTimeInputTable <- EitherT(loadOptionalInputTable(pipeline.outOfTimeInputTableId))
      allInputTables = inputTable :: List(holdOutInputTable, outOfTimeInputTable).flatten
      _ <- EitherT.fromEither[Future](validateTablesStatuses(allInputTables))
      weightColumn = pipeline.samplingWeightColumnName.map(buildWeightColumn)
      _ <- EitherT.fromEither[Future](validateColumnsInTables(allInputTables, weightColumn))
    } yield Params(
      inputTable,
      holdOutInputTable,
      outOfTimeInputTable,
      pipeline.responseColumn,
      pipeline.predictorColumns,
      weightColumn
    )

    result.value
  }

  // scalastyle:off method.length
  override protected def createPipeline(
    params: Params,
    pipeline: TabularTrainPipeline,
    experimentName: String,
    experimentDescription: Option[String]
  )(implicit user: User): Future[PipelineCreatedResult[TabularTrainPipeline]] = {

    def createOutputTable(): Future[WithId[Table]] =
      tabularModelCommonService.createOutputTable(
        name = None,
        columns = Seq.empty,
        user = user
      )

    def createOptionalTable(opt: Option[_]): Future[Option[WithId[Table]]] =
      opt match {
        case Some(_) => createOutputTable().map(Some(_))
        case None => Future.successful(None)
      }

    def saveModel(experimentId: String, modelPackageId: String): Future[WithId[TabularModel]] =
      for {
        modelName <- UniqueNameGenerator.generateUniqueName(
          experimentName + " Model",
          " "
        )(name => modelDao.count(OwnerIdIs(user.id) && NameIs(name)).map(_ == 0))
        now = Instant.now()
        model = TabularModel(
          ownerId = user.id,
          name = modelName,
          predictorColumns = params.predictorColumns,
          responseColumn = params.responseColumn,
          classNames = None,
          classReference = ClassReference(
            packageId = modelPackageId,
            moduleName = modelModuleName,
            className = modelClassName
          ),
          cortexModelReference = None,
          inLibrary = false,
          status = TabularModelStatus.Training,
          created = now,
          updated = now,
          description = experimentDescription,
          experimentId = Some(experimentId)
        )
        id <- modelDao.create(model)
      } yield WithId(model, id)

    for {
      // TODO What if pass this predefined package in the constructor? We don't really need to load it every time
      modelPackage <- packageService.getPackageByNameAndVersion(modelPackageName, None).map(_.get)
      outputTable <- createOutputTable()
      holdOutOutputTable <- createOptionalTable(params.holdOutInputTable)
      outOfTimeOutputTable <- createOptionalTable(params.outOfTimeInputTable)
      inputDataSource <- tableService.buildDataSource(params.inputTable.entity).toFuture
      outputDataSource <- tableService.buildDataSource(outputTable.entity).toFuture
      predictionResultColumnName = tabularModelCommonService.generatePredictionResultColumnName(
        responseColumnName = params.responseColumn.name,
        tableColumnsNames = params.inputTable.entity.columns.map(_.name)
      )
      experimentCreatedHandler = { experimentId: String =>
        for {
          model <- saveModel(experimentId, modelPackage.id)
          request = TrainRequest(
            input = Some(inputDataSource),
            predictors = params.predictorColumns.map(columnToCortexColumn),
            response = Some(columnToCortexColumn(params.responseColumn)),
            weight = params.weightColumn.map(columnToCortexColumn),
            output = Some(outputDataSource),
            dropPreviousResultTable = false,
            predictionResultColumnName = predictionResultColumnName,
            probabilityColumnsPrefix = Some(tabularModelCommonService.probabilityColumnsPrefix)
          )
          jobId <- cortexJobService.submitJob(request, user.id)
          process <- processService.startProcess(
            jobId,
            experimentId,
            AssetType.Experiment,
            classOf[TabularModelTrainResultHandler],
            TabularModelTrainResultHandler.Meta(
              modelId = model.id,
              inputTableId = params.inputTable.id,
              outputTableId = outputTable.id,
              holdOutEvaluationParams = (params.holdOutInputTable.map(_.id), holdOutOutputTable.map(_.id)).mapN(
                EvaluationParams(_, _)
              ),
              outOfTimeEvaluationParams = (params.outOfTimeInputTable.map(_.id), outOfTimeOutputTable.map(_.id)).mapN(
                EvaluationParams(_, _)
              ),
              experimentId = experimentId,
              predictionResultColumnName = predictionResultColumnName,
              userId = user.id
            ),
            user.id
          )
        } yield process
      }
    } yield {
      PipelineCreatedResult(
        handler = experimentCreatedHandler,
        pipeline = pipeline
      )
    }
  }
  // scalastyle:on method.length

  private[model] def launchEvaluation(
    model: WithId[TabularModel],
    experimentId: String,
    evaluationParams: TypedEvaluationParams,
    nextStepEvaluationParams: Option[TypedEvaluationParams],
    samplingWeightColumnName: Option[String],
    userId: UUID
  ): Future[Unit] = {

    def buildJobMessage(
      cortexId: String,
      inputTable: Table,
      outputTable: Table,
      predictionResultColumnName: String,
      classProbabilityColumns: Seq[(String, Column)],
      packageLocation: Option[String]
    ): Try[EvaluateRequest] =
      for {
        inputDataSource <- tableService.buildDataSource(inputTable)
        outputDataSource <- tableService.buildDataSource(outputTable)
      } yield {
        EvaluateRequest(
          modelId = cortexId,
          input = Some(inputDataSource),
          predictors = model.entity.predictorColumns.map(column => buildTrivialColumnMapping(column.name)),
          weight = samplingWeightColumnName.map(buildTrivialColumnMapping),
          response = Some(buildTrivialColumnMapping(model.entity.responseColumn.name)),
          output = Some(outputDataSource),
          dropPreviousResultTable = false,
          predictionResultColumnName = predictionResultColumnName,
          modelReference = Some(CortexClassReference(
            packageLocation = packageLocation,
            moduleName = model.entity.classReference.moduleName,
            className = model.entity.classReference.className
          )),
          probabilityColumns = classProbabilityColumns.map { case (className, column) =>
            ProbabilityClassColumn(
              className = className,
              columnName = column.name
            )
          }
        )
      }

    def buildOutputColumns(inputTableColumns: Seq[Column], classProbabilityColumns: Seq[Column]): Seq[Column] = {
      val predictionResultColumnName = tabularModelCommonService.generatePredictionResultColumnName(
        model.entity.responseColumn.name,
        inputTableColumns.map(_.name)
      )
      val predictionResultColumn = buildPredictionResultColumn(predictionResultColumnName, model.entity.responseColumn)
      (predictionResultColumn +: classProbabilityColumns) ++ inputTableColumns
    }

    for {
      cortexId <- tabularModelCommonService.getCortexId(model).toFuture
      inputTable <- tableService.loadTableMandatory(evaluationParams.baseParams.inputTableId)
      outputTable <- tableService.loadTableMandatory(evaluationParams.baseParams.outputTableId)
      modelPackage <- packageService.loadPackageMandatory(model.entity.classReference.packageId)
      inputTableColumns = inputTable.entity.columns
      predictionResultColumnName = tabularModelCommonService.generatePredictionResultColumnName(
        model.entity.responseColumn.name,
        inputTableColumns.map(_.name)
      )
      classProbabilityColumns = tabularModelCommonService.generateProbabilityColumnsForClasses(
        model.entity,
        inputTable.entity
      )
      outputTableColumns = buildOutputColumns(
        inputTableColumns,
        classProbabilityColumns.map { case (_, column) => column }
      )
      _ <- tableService.updateTable(outputTable.id, _.copy(columns = outputTableColumns))
      jobMessage <- buildJobMessage(
        cortexId,
        inputTable.entity,
        outputTable.entity,
        predictionResultColumnName,
        classProbabilityColumns,
        modelPackage.entity.location
      ).toFuture
      evaluateJobId <- cortexJobService.submitJob(jobMessage, userId)
      _ <- processService.startProcess(
        jobId = evaluateJobId,
        targetId = experimentId,
        targetType = AssetType.Experiment,
        handlerClass = classOf[TabularModelEvaluateResultHandler],
        meta = TabularModelEvaluateResultHandler.Meta(
          experimentId = experimentId,
          evaluationType = evaluationParams.evaluationType,
          nextStepParams = nextStepEvaluationParams,
          outputTableId = evaluationParams.baseParams.outputTableId,
          userId = userId
        ),
        userId = userId
      )
    } yield ()
  }

  private[model] def buildPredictionResultColumn(columnName: String, modelResponseColumn: ModelColumn): Column =
    Column(
      name = columnName,
      displayName = modelResponseColumn.displayName + " predicted",
      dataType = modelResponseColumn.dataType,
      variableType = modelResponseColumn.variableType,
      align = tableService.getColumnAlignment(modelResponseColumn.dataType),
      statistics = None
    )

  private[model] def updateOutputEntitiesOnSuccess(
    result: TabularTrainResult
  ): Future[Unit] = {

    def unlockOutputTable(tableId: Option[String]): Future[Unit] =
      tableId match {
        case Some(id) => tableService.updateStatus(id, TableStatus.Active)
        case None => Future.unit
      }

    for {
      _ <- tabularModelCommonService.updateModelStatus(result.modelId, TabularModelStatus.Active)
      _ <- unlockOutputTable(Some(result.outputTableId))
      _ <- unlockOutputTable(result.holdOutOutputTableId)
      _ <- unlockOutputTable(result.outOfTimeOutputTableId)
    } yield ()
  }

  private[model] def updateOutputEntitiesOnNoSuccess[S <: ExperimentStatus: NonSuccessfulTerminalStatus](
    result: TabularTrainResult,
    status: S
  ): Future[Unit] = {

    def failOutputTable(tableId: Option[String]): Future[Unit] =
      tableId match {
        case Some(id) => tabularModelCommonService.failTable(id)
        case None => Future.unit
      }

    for {
      _ <- tabularModelCommonService.updateModelStatus(
        result.modelId,
        implicitly[NonSuccessfulTerminalStatus[S]].correspondingModelStatus
      )
      _ <- failOutputTable(Some(result.outputTableId))
      _ <- failOutputTable(result.holdOutOutputTableId)
      _ <- failOutputTable(result.outOfTimeOutputTableId)
    } yield ()
  }


  private def buildTrivialColumnMapping(columnName: String) =
    ColumnMapping(
      trainName = columnName,
      currentName = columnName
    )

  private def validateColumnsInTable(columns: List[ModelColumn], table: WithId[Table]): CreateErrorOr[Unit] = {

    def validateColumnInTable(column: ModelColumn, table: WithId[Table]): CreateErrorOr[Unit] =
      for {
        tableColumn <- findColumnInTable(column.name, table)
        _ <- {
          val allowedTypes = tableService.allowedTypeConversions(tableColumn.dataType)
          if (allowedTypes.contains(column.dataType)) ().asRight
          else InvalidColumnDataType(column, allowedTypes).asLeft
        }
      } yield ()

    columns.map(validateColumnInTable(_, table)).sequence[CreateErrorOr, Unit].map(_ => ())
  }

  private def findColumnInTable(columnName: String, table: WithId[Table]): CreateErrorOr[Column] =
    table.entity.columns.find(_.name == columnName) match {
      case Some(c) => c.asRight
      case None => ColumnNotFoundInTable(columnName, table.id).asLeft
    }

  private def buildWeightColumn(weightColumnName: String): ModelColumn =
    ModelColumn(
      name = weightColumnName,
      displayName = weightColumnName,
      dataType = ColumnDataType.Double,
      variableType = ColumnVariableType.Continuous
    )

  private def columnToCortexColumn(column: ModelColumn): CortexColumn =
    CortexColumn(
      name = column.name,
      dataType = tableService.dataTypeToCortexDataType(column.dataType),
      variableType = tableService.variableTypeToCortexVariableType(column.variableType)
    )

}

object TabularTrainPipelineHandler {

  sealed trait TabularModelCreateError extends CreateError
  object TabularModelCreateError {
    case class TableNotFound(id: String) extends TabularModelCreateError
    case class TableNotActive(id: String) extends TabularModelCreateError
    case class ColumnNotFoundInTable(columnName: String, tableId: String) extends TabularModelCreateError
    case class InvalidColumnDataType(
      column: ModelColumn,
      allowedTypes: Set[ColumnDataType]
    ) extends TabularModelCreateError
  }

  case class Params(
    inputTable: WithId[Table],
    holdOutInputTable: Option[WithId[Table]],
    outOfTimeInputTable: Option[WithId[Table]],
    responseColumn: ModelColumn,
    predictorColumns: List[ModelColumn],
    weightColumn: Option[ModelColumn]
  )

  private[model] sealed trait EvaluationType
  private[model] object EvaluationType {
    case object HoldOut extends EvaluationType
    case object OutOfTime extends EvaluationType
  }

  private[model] case class EvaluationParams(inputTableId: String, outputTableId: String)
  private[model] case class TypedEvaluationParams(baseParams: EvaluationParams, evaluationType: EvaluationType)

  private[model] sealed trait NonSuccessfulTerminalStatus[S] {
    val correspondingModelStatus: TabularModelStatus
  }

  private[model] implicit val CancelledNonSuccessfulTerminalStatus: NonSuccessfulTerminalStatus[
    ExperimentStatus.Cancelled.type
    ] = new NonSuccessfulTerminalStatus[ExperimentStatus.Cancelled.type] {
    override val correspondingModelStatus: TabularModelStatus = TabularModelStatus.Cancelled
  }

  private[model] implicit val ErrorNonSuccessfulTerminalStatus: NonSuccessfulTerminalStatus[
    ExperimentStatus.Error.type
    ] = new NonSuccessfulTerminalStatus[ExperimentStatus.Error.type] {
    override val correspondingModelStatus: TabularModelStatus = TabularModelStatus.Error
  }

}
