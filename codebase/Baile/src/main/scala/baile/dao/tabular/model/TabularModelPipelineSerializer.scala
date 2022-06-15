package baile.dao.tabular.model

import baile.dao.CommonSerializers
import baile.dao.experiment.PipelineSerializer
import baile.dao.experiment.SerializerDelegator.HasAssetReference
import baile.dao.mongo.BsonHelpers._
import baile.daocommons.filters.Filter
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.tabular.model.summary._
import baile.domain.tabular.model.{ ModelColumn, TabularModelClass }
import baile.domain.tabular.pipeline.TabularTrainPipeline
import baile.domain.tabular.result.TabularTrainResult
import org.mongodb.scala.Document
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{ BsonArray, BsonDocument, BsonDouble, BsonInt32, BsonString }
import org.mongodb.scala.model.Filters

import scala.util.Try

class TabularModelPipelineSerializer extends PipelineSerializer[TabularTrainPipeline, TabularTrainResult] {

  override val filterMapper: PartialFunction[Filter, Try[Bson]] = {
    case HasAssetReference(AssetReference(tableId, AssetType.Table)) => Try(Filters.or(
      Filters.equal("pipeline.inputTableId", tableId),
      Filters.equal("pipeline.holdOutInputTableId", tableId),
      Filters.equal("pipeline.outOfTimeInputTableId", tableId),
      Filters.equal("result.outputTableId", tableId),
      Filters.equal("result.holdOutOutputTableId", tableId),
      Filters.equal("result.outOfTimeOutputTableId", tableId)
    ))
    case HasAssetReference(AssetReference(modelId, AssetType.TabularModel)) => Try(
      Filters.equal("result.modelId", modelId)
    )
  }

  override def pipelineToDocument(tabularTrainPipeline: TabularTrainPipeline): Document = BsonDocument(
    "samplingWeightColumnName" -> tabularTrainPipeline.samplingWeightColumnName.map(BsonString(_)),
    "predictorColumns" -> tabularTrainPipeline.predictorColumns.map(columnToDocument),
    "responseColumn" -> columnToDocument(tabularTrainPipeline.responseColumn),
    "inputTableId" -> BsonString(tabularTrainPipeline.inputTableId),
    "holdOutInputTableId" -> tabularTrainPipeline.holdOutInputTableId.map(BsonString(_)),
    "outOfTimeInputTableId" -> tabularTrainPipeline.outOfTimeInputTableId.map(BsonString(_))
  )

  override def documentToPipeline(document: Document): TabularTrainPipeline = TabularTrainPipeline(
    samplingWeightColumnName = document.get[BsonString]("samplingWeightColumnName").map(_.getValue),
    predictorColumns = document.getMandatory[BsonArray]("predictorColumns").map(elem =>
      documentToColumn(elem.asDocument)
    ).toList,
    responseColumn = documentToColumn(document.getChildMandatory("responseColumn")),
    inputTableId = document.getMandatory[BsonString]("inputTableId").getValue,
    holdOutInputTableId = document.get[BsonString]("holdOutInputTableId").map(_.getValue),
    outOfTimeInputTableId = document.get[BsonString]("outOfTimeInputTableId").map(_.getValue)
  )

  override def resultToDocument(tabularTrainResult: TabularTrainResult): Document = BsonDocument(
    "modelId" -> BsonString(tabularTrainResult.modelId),
    "outputTableId" -> BsonString(tabularTrainResult.outputTableId),
    "holdOutOutputTableId" -> tabularTrainResult.holdOutOutputTableId.map(BsonString(_)),
    "outOfTimeOutputTableId" -> tabularTrainResult.outOfTimeOutputTableId.map(BsonString(_)),
    "predictedColumnName" -> tabularTrainResult.predictedColumnName,
    "classes" -> tabularTrainResult.classes.map(_.map(classToDocument)),
    "summary" -> tabularTrainResult.summary.map(summaryToDocument),
    "holdOutSummary" -> tabularTrainResult.holdOutSummary.map(summaryToDocument),
    "outOfTimeSummary" -> tabularTrainResult.outOfTimeSummary.map(summaryToDocument),
    "predictorsSummary" -> tabularTrainResult.predictorsSummary.map(predictorSummaryToDocument)
  )

  override def documentToResult(document: Document): TabularTrainResult = TabularTrainResult(
    modelId = document.getMandatory[BsonString]("modelId").getValue,
    outputTableId = document.getMandatory[BsonString]("outputTableId").getValue,
    holdOutOutputTableId = document.get[BsonString]("holdOutOutputTableId").map(_.getValue),
    outOfTimeOutputTableId = document.get[BsonString]("outOfTimeOutputTableId").map(_.getValue),
    predictedColumnName = document.getMandatory[BsonString]("predictedColumnName").getValue,
    classes = document.get[BsonArray]("classes").map(_.map(elem => documentToClass(elem.asDocument))),
    summary = document.getChild("summary").map(documentToTrainSummary),
    holdOutSummary = document.getChild("holdOutSummary").map(documentToEvaluationSummary),
    outOfTimeSummary = document.getChild("outOfTimeSummary").map(documentToEvaluationSummary),
    predictorsSummary = document.getMandatory[BsonArray]("predictorsSummary").map(elem =>
      documentToPredictorSummary(elem.asDocument)
    )
  )

  private def classToDocument(modelClass: TabularModelClass): Document = Document(
    "className" -> BsonString(modelClass.className),
    "probabilityColumnName" -> BsonString(modelClass.probabilityColumnName)
  )

  private def documentToClass(document: Document): TabularModelClass = TabularModelClass(
    className = document.getMandatory[BsonString]("className").getValue,
    probabilityColumnName = document.getMandatory[BsonString]("probabilityColumnName").getValue
  )

  private def columnToDocument(column: ModelColumn): Document = Document(
    "name" -> BsonString(column.name),
    "displayName" -> BsonString(column.displayName),
    "dataType" -> BsonString(CommonSerializers.columnDataTypeToString(column.dataType)),
    "variableType" -> BsonString(CommonSerializers.columnVariableTypeToString(column.variableType))
  )

  private def documentToColumn(document: Document): ModelColumn = ModelColumn(
    name = document.getMandatory[BsonString]("name").getValue,
    displayName = document.getMandatory[BsonString]("displayName").getValue,
    dataType = CommonSerializers.columnDataTypeFromString(document.getMandatory[BsonString]("dataType").getValue),
    variableType = CommonSerializers.columnVariableTypeFromString(
      document.getMandatory[BsonString]("variableType").getValue
    )
  )

  private def summaryToDocument(summary: TabularModelEvaluationSummary): Document = summary match {
    case summary: RegressionSummary => regressionSummaryToDocument(summary)
    case summary: ClassificationSummary => classificationSummaryToDocument(summary)
    case summary: BinaryClassificationEvaluationSummary => binaryClassificationEvaluationSummaryToDocument(summary)
  }

  private def summaryToDocument(summary: TabularModelTrainSummary): Document = summary match {
    case summary: RegressionSummary => regressionSummaryToDocument(summary)
    case summary: ClassificationSummary => classificationSummaryToDocument(summary)
    case summary: BinaryClassificationTrainSummary => binaryClassificationTrainSummaryToDocument(summary)
  }

  private def regressionSummaryToDocument(summary: RegressionSummary): Document =
    Document(
      "rmse" -> BsonDouble(summary.rmse),
      "r2" -> BsonDouble(summary.r2),
      "mape" -> BsonDouble(summary.mape),
      "__summaryType" -> BsonString("regression")
    )

  private def classificationSummaryToDocument(classificationSummary: ClassificationSummary): Document =
    Document(
      "confusionMatrix" -> classificationSummary.confusionMatrix.map { classConfusion =>
        Document(
          "className" -> BsonString(classConfusion.className),
          "trueNegative" -> BsonInt32(classConfusion.trueNegative),
          "truePositive" -> BsonInt32(classConfusion.truePositive),
          "falseNegative" -> BsonInt32(classConfusion.falseNegative),
          "falsePositive" -> BsonInt32(classConfusion.falsePositive)
        )
      },
      "__summaryType" -> BsonString("classificationGeneral")
    )

  private def binaryClassificationEvaluationSummaryToDocument(
    summary: BinaryClassificationEvaluationSummary
  ): Document =
    Document(
      "classificationSummary" -> classificationSummaryToDocument(summary.classificationSummary),
      "ks" -> BsonDouble(summary.ks),
      "__summaryType" -> BsonString("classificationBinaryEvaluation")
    )

  private def binaryClassificationTrainSummaryToDocument(
    summary: BinaryClassificationTrainSummary
  ): Document = Document(
    "evaluationSummary" -> binaryClassificationEvaluationSummaryToDocument(summary.evaluationSummary),
    "areaUnderROC" -> BsonDouble(summary.areaUnderROC),
    "rocValues" -> summary.rocValues.map { case RocValue(falsePositive, truePositive) =>
      Document(
        "falsePositive" -> BsonDouble(falsePositive),
        "truePositive" -> BsonDouble(truePositive)
      )
    },
    "f1Score" -> BsonDouble(summary.f1Score),
    "precision" -> BsonDouble(summary.precision),
    "recall" -> BsonDouble(summary.recall),
    "threshold" -> BsonDouble(summary.threshold),
    "__summaryType" -> BsonString("classificationBinaryTrain")
  )

  private def predictorSummaryToDocument(
    predictorSummary: PredictorSummary
  ): Document = predictorSummary match {
    case summary: ParametricModelPredictorSummary =>
      Document(
        "name" -> BsonString(summary.name),
        "coefficient" -> BsonDouble(summary.coefficient),
        "stdErr" -> BsonDouble(summary.stdErr),
        "tValue" -> BsonDouble(summary.tValue),
        "pValue" -> BsonDouble(summary.pValue),
        "__summaryType" -> BsonString("parametric")
      )
    case TreeModelPredictorSummary(name, importance) =>
      Document(
        "name" -> BsonString(name),
        "importance" -> BsonDouble(importance),
        "__summaryType" -> BsonString("tree")
      )
  }

  private def documentToTrainSummary(document: Document): TabularModelTrainSummary =
    document.getMandatory[BsonString]("__summaryType").getValue match {
      case "regression" =>
        documentToRegressionSummary(document)
      case "classificationGeneral" =>
        documentToClassificationSummary(document)
      case "classificationBinaryTrain" =>
        documentToBinaryClassificationTrainSummary(document)
      case unknown =>
        throw new RuntimeException(s"Unknown tabular model train summary type $unknown for document $document")
    }

  private def documentToEvaluationSummary(document: Document): TabularModelEvaluationSummary =
    document.getMandatory[BsonString]("__summaryType").getValue match {
      case "regression" =>
        documentToRegressionSummary(document)
      case "classificationGeneral" =>
        documentToClassificationSummary(document)
      case "classificationBinaryEvaluation" =>
        documentToBinaryClassificationEvaluationSummary(document)
      case unknown =>
        throw new RuntimeException(s"Unknown tabular model evaluation summary type $unknown for document $document")
    }

  private def documentToRegressionSummary(document: Document): RegressionSummary =
    RegressionSummary(
      rmse = document.getMandatory[BsonDouble]("rmse").getValue,
      r2 = document.getMandatory[BsonDouble]("r2").getValue,
      mape = document.getMandatory[BsonDouble]("mape").getValue
    )

  private def documentToClassificationSummary(document: Document): ClassificationSummary =
    ClassificationSummary(
      confusionMatrix = document.getMandatory[BsonArray]("confusionMatrix").map { elem =>
        val elemDocument = Document(elem.asDocument)
        ClassConfusion(
          className = elemDocument.getMandatory[BsonString]("className").getValue,
          trueNegative = elemDocument.getMandatory[BsonInt32]("trueNegative").getValue,
          truePositive = elemDocument.getMandatory[BsonInt32]("truePositive").getValue,
          falseNegative = elemDocument.getMandatory[BsonInt32]("falseNegative").getValue,
          falsePositive = elemDocument.getMandatory[BsonInt32]("falsePositive").getValue
        )
      }
    )

  private def documentToBinaryClassificationEvaluationSummary(
    document: Document
  ): BinaryClassificationEvaluationSummary =
    BinaryClassificationEvaluationSummary(
      classificationSummary = documentToClassificationSummary(
        document.getChildMandatory("classificationSummary")
      ),
      ks = document.getMandatory[BsonDouble]("ks").getValue
    )

  private def documentToBinaryClassificationTrainSummary(
    document: Document
  ): BinaryClassificationTrainSummary =
    BinaryClassificationTrainSummary(
      evaluationSummary = documentToBinaryClassificationEvaluationSummary(
        document.getChildMandatory("evaluationSummary")
      ),
      areaUnderROC = document.getMandatory[BsonDouble]("areaUnderROC").getValue,
      rocValues = document.getMandatory[BsonArray]("rocValues").map { elem =>
        val elemDocument = Document(elem.asDocument)
        RocValue(
          elemDocument.getMandatory[BsonDouble]("falsePositive").getValue,
          elemDocument.getMandatory[BsonDouble]("truePositive").getValue
        )
      },
      f1Score = document.getMandatory[BsonDouble]("f1Score").getValue,
      precision = document.getMandatory[BsonDouble]("precision").getValue,
      recall = document.getMandatory[BsonDouble]("recall").getValue,
      threshold = document.getMandatory[BsonDouble]("threshold").getValue
    )

  private def documentToPredictorSummary(document: Document): PredictorSummary =
    document.getMandatory[BsonString]("__summaryType").getValue match {
      case "parametric" =>
        ParametricModelPredictorSummary(
          name = document.getMandatory[BsonString]("name").getValue,
          coefficient = document.getMandatory[BsonDouble]("coefficient").getValue,
          stdErr = document.getMandatory[BsonDouble]("stdErr").getValue,
          tValue = document.getMandatory[BsonDouble]("tValue").getValue,
          pValue = document.getMandatory[BsonDouble]("pValue").getValue
        )
      case "tree" =>
        TreeModelPredictorSummary(
          name = document.getMandatory[BsonString]("name").getValue,
          importance = document.getMandatory[BsonDouble]("importance").getValue
        )
      case unknown =>
        throw new RuntimeException(s"Unknown tabular model predictor summary type $unknown for document $document")
    }

}
