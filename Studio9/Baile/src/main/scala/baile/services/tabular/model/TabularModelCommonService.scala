package baile.services.tabular.model

import baile.dao.experiment.ExperimentDao
import baile.dao.tabular.model.TabularModelDao
import baile.daocommons.WithId
import baile.domain.table._
import baile.domain.tabular.model.summary._
import baile.domain.tabular.model.{ TabularModel, TabularModelStatus }
import baile.domain.usermanagement.User
import baile.services.common.EntityUpdateFailedException
import baile.services.table.TableService
import baile.utils.UniqueNameGenerator
import cats.Id
import cortex.api.job.tabular.{
  EvaluationResult,
  TrainResult,
  BinaryClassificationEvalSummary => CortexBinaryClassificationEvaluationSummary,
  BinaryClassificationTrainSummary => CortexBinaryClassificationTrainSummary,
  ClassificationSummary => CortexClassificationSummary,
  PredictorSummary => CortexPredictorSummary,
  RegressionSummary => CortexRegressionSummary,
  RocValue => CortexRocValue
}

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class TabularModelCommonService(
  modelDao: TabularModelDao,
  experimentDao: ExperimentDao,
  tableService: TableService,
  private[tabular] val probabilityColumnsPrefix: String
)(implicit val ec: ExecutionContext) {

  private[tabular] def loadModelMandatory(id: String): Future[WithId[TabularModel]] =
    modelDao.get(id).map(_.getOrElse(throw new RuntimeException(
      s"Unexpectedly not found model $id in storage"
    )))

  private[tabular] def generatePredictionResultColumnName(
    responseColumnName: String,
    tableColumnsNames: Seq[String]
  ): String =
    UniqueNameGenerator.generateUniqueName[Id](
      responseColumnName + "_predicted"
    )(!tableColumnsNames.contains(_))

  private[tabular] def generatePredictionResultColumnDisplayName(
    responseColumnDisplayName: String,
    tableColumnsDisplayNames: Seq[String]
  ): String = {
    if (tableColumnsDisplayNames.contains(responseColumnDisplayName)) {
      UniqueNameGenerator.generateUniqueName[Id](
        responseColumnDisplayName + " (predicted)",
        suffixDelimiter = " "
      )(!tableColumnsDisplayNames.contains(_))
    } else {
      responseColumnDisplayName
    }
  }

  private[tabular] def generateProbabilityColumnsForClasses(
    model: TabularModel,
    inputTable: Table
  ): Seq[(String, Column)] = {

    val inputTableColumnNames = inputTable.columns.map(_.name)

    model.classNames match {
      case Some(probabilityClasses) =>
        probabilityClasses.foldLeft(Seq.empty[(String, Column)]) { case (soFar, className) =>
          val existingColumnNames = inputTableColumnNames ++ soFar.map { case (_, column) => column.name }
          val classProbabilityColumnName = UniqueNameGenerator.generateUniqueName[Id](
            tableService.normalizeName(probabilityColumnsPrefix + className)
          )(!existingColumnNames.contains(_))
          val classProbabilityColumn = Column(
            name = classProbabilityColumnName,
            displayName = buildProbabilityColumnDisplayName(className),
            dataType = ColumnDataType.Double,
            variableType = ColumnVariableType.Continuous,
            align = tableService.getColumnAlignment(ColumnDataType.Double),
            statistics = None
          )
          soFar :+ className -> classProbabilityColumn
        }
      case None =>
        Seq.empty
    }
  }

  private[tabular] def buildProbabilityColumnDisplayName(className: String): String =
    s"$className probability"

  private[tabular] def assertModelStatus(
    model: WithId[TabularModel],
    expectedStatus: TabularModelStatus
  ): Try[Unit] = Try {
    if (model.entity.status != expectedStatus) {
      throw new RuntimeException(
        s"Unexpected model status ${ model.entity.status } for model ${ model.id }. Expected $expectedStatus"
      )
    } else {
      ()
    }
  }

  private[tabular] def failTable(tableId: String): Future[Unit] =
    tableService.updateStatus(tableId, TableStatus.Error)

  private[tabular] def updateModelStatus(modelId: String, status: TabularModelStatus): Future[WithId[TabularModel]] =
    modelDao.update(modelId, _.copy(status = status)).map(
      _.getOrElse(throw EntityUpdateFailedException(modelId, classOf[TabularModel]))
    )

  private[tabular] def getCortexId(model: WithId[TabularModel]): Try[String] =
    Try(model.entity.cortexModelReference.getOrElse(throw CortexModelIdNotFoundException(model.id)).cortexId)

  private[tabular] def createOutputTable(
    name: Option[String],
    columns: Seq[Column],
    user: User
  ): Future[WithId[Table]] =
    tableService.createEmptyTable(
      name = name,
      tableType = TableType.Derived,
      columns = columns,
      inLibrary = false,
      user = user
    )

  private[tabular] def buildTabularModelTrainSummary(cortexSummary: TrainResult.Summary): TabularModelTrainSummary =
    cortexSummary match {
      case TrainResult.Summary.BinaryClassificationTrainSummary(summary) =>
        buildBinaryClassificationTrainSummary(summary)
      case TrainResult.Summary.ClassificationSummary(summary) =>
        buildClassificationSummary(summary)
      case TrainResult.Summary.RegressionSummary(summary) =>
        buildRegressionSummary(summary)
      case TrainResult.Summary.Empty =>
        throw new RuntimeException("Unexpected empty value in tabular train result summary")
    }

  private[tabular] def buildTabularModelEvaluationSummary(
    cortexSummary: EvaluationResult.Summary
  ): TabularModelEvaluationSummary = cortexSummary match {
    case EvaluationResult.Summary.BinaryClassificationEvalSummary(summary) =>
      buildBinaryClassificationEvaluationSummary(summary)
    case EvaluationResult.Summary.ClassificationSummary(summary) =>
      buildClassificationSummary(summary)
    case EvaluationResult.Summary.RegressionSummary(summary) =>
      buildRegressionSummary(summary)
    case EvaluationResult.Summary.Empty =>
      throw new RuntimeException("Unexpected empty value in tabular evaluation result summary")
  }

  private[tabular] def buildBinaryClassificationTrainSummary(summary: CortexBinaryClassificationTrainSummary) =
    BinaryClassificationTrainSummary(
      evaluationSummary = buildBinaryClassificationEvaluationSummary(summary.getBinaryClassificationEvalSummary),
      areaUnderROC = summary.areaUnderRoc,
      rocValues = convertCortexRocValues(summary.rocValues),
      f1Score = summary.f1Score,
      precision = summary.precision,
      recall = summary.recall,
      threshold = summary.threshold
    )

  private[tabular] def buildClassificationSummary(summary: CortexClassificationSummary) =
    ClassificationSummary(summary.confusionMatrix.map { classConfusion =>
      ClassConfusion(
        className = classConfusion.className,
        truePositive = classConfusion.truePositive,
        trueNegative = classConfusion.trueNegative,
        falsePositive = classConfusion.falsePositive,
        falseNegative = classConfusion.falseNegative
      )
    })

  private[tabular] def convertCortexRocValues(rocValues: Seq[CortexRocValue]): Seq[RocValue] =
    rocValues.map { rocValue =>
      RocValue(
        falsePositive = rocValue.falsePositive,
        truePositive= rocValue.truePositive
      )
    }

  private[tabular] def buildRegressionSummary(summary: CortexRegressionSummary): RegressionSummary =
    RegressionSummary(
      rmse = summary.rmse,
      r2 = summary.r2,
      mape = summary.mape
    )

  private[tabular] def buildBinaryClassificationEvaluationSummary(
    summary: CortexBinaryClassificationEvaluationSummary
  ): BinaryClassificationEvaluationSummary =
    BinaryClassificationEvaluationSummary(
      classificationSummary = buildClassificationSummary(summary.getGeneralClassificationSummary),
      ks = summary.ks
    )

  private[tabular] def buildPredictorSummary(predictorSummary: CortexPredictorSummary): PredictorSummary =
    predictorSummary.summary match {
      case CortexPredictorSummary.Summary.ParametricModelPredictorSummary(summary) =>
        ParametricModelPredictorSummary(
          name = predictorSummary.name,
          coefficient = summary.coefficient,
          stdErr = summary.stdErr,
          tValue = summary.tValue,
          pValue = summary.pValue
        )
      case CortexPredictorSummary.Summary.TreeModelPredictorSummary(summary) =>
        TreeModelPredictorSummary(
          name = predictorSummary.name,
          importance = summary.importance
        )
      case CortexPredictorSummary.Summary.Empty =>
        throw new RuntimeException("Unexpected empty value in tabular predictor summary")
    }

}
