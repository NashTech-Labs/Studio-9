package baile.dao.cv.model

import baile.dao.CommonSerializers
import baile.dao.cv.AugmentationSerializers
import baile.dao.cv.model.CVModelPipelineSerializer._
import baile.dao.experiment.PipelineSerializer
import baile.dao.experiment.SerializerDelegator.HasAssetReference
import baile.dao.mongo.BsonHelpers._
import baile.daocommons.filters.Filter
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.pipeline.PipelineParams._
import baile.domain.cv.CommonTrainParams.InputSize
import baile.domain.cv.model.{ CVModelSummary, CVModelTrainTimeSpentSummary }
import baile.domain.cv.pipeline._
import baile.domain.cv.result.{ CVTLTrainResult, CVTLTrainStepResult }
import baile.domain.cv.{ CommonTrainParams, EvaluateTimeSpentSummary, LabelOfInterest }
import org.mongodb.scala.Document
import org.mongodb.scala.bson._
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters

import scala.util.Try

class CVModelPipelineSerializer extends PipelineSerializer[CVTLTrainPipeline, CVTLTrainResult] {

  override val filterMapper: PartialFunction[Filter, Try[Bson]] = {
    case HasAssetReference(AssetReference(albumId, AssetType.Album)) => Try(Filters.or(
      Filters.equal("pipeline.stepOne.inputAlbumId", albumId),
      Filters.equal("pipeline.stepOne.testInputAlbumId", albumId),
      Filters.equal("pipeline.stepTwo.inputAlbumId", albumId),
      Filters.equal("pipeline.stepTwo.testInputAlbumId", albumId),
      Filters.equal("result.stepOne.outputAlbumId", albumId),
      Filters.equal("result.stepOne.testOutputAlbumId", albumId),
      Filters.equal("result.stepOne.autoAugmentationSampleAlbumId", albumId),
      Filters.equal("result.stepTwo.outputAlbumId", albumId),
      Filters.equal("result.stepTwo.testOutputAlbumId", albumId),
      Filters.equal("result.stepTwo.autoAugmentationSampleAlbumId", albumId)
    ))
    case HasAssetReference(AssetReference(modelId, AssetType.CvModel)) => Try(Filters.or(
      Filters.equal("pipeline.stepOne.feParams.featureExtractorModelId", modelId),
      Filters.equal("result.stepOne.modelId", modelId),
      Filters.equal("result.stepTwo.modelId", modelId)
    ))
    case HasAssetReference(AssetReference(tableId, AssetType.Table)) => Try(Filters.or(
      Filters.equal("result.stepOne.probabilityPredictionTableId", tableId),
      Filters.equal("result.stepOne.testProbabilityPredictionTableId", tableId),
      Filters.equal("result.stepTwo.probabilityPredictionTableId", tableId),
      Filters.equal("result.stepTwo.testProbabilityPredictionTableId", tableId)
    ))
    case OperatorIdIs(operatorId) => Try(
      Filters.or(
        Filters.equal("pipeline.stepOne.modelType.operatorId", operatorId),
        Filters.equal("pipeline.stepTwo.modelType.operatorId", operatorId)
      )
    )
  }

  override def pipelineToDocument(pipeline: CVTLTrainPipeline): Document = BsonDocument(
    "stepOne" -> cvTLTrainStepOneParamsToDocument(pipeline.stepOne),
    "stepTwo" -> pipeline.stepTwo.map(cVTLTrainStepTwoParamsToDocument)
  )

  override def documentToPipeline(document: Document): CVTLTrainPipeline = CVTLTrainPipeline(
    stepOne = documentToCVTLTrainStepOneParams(document.getChildMandatory("stepOne")),
    stepTwo = document.getChild("stepTwo").map(documentToCVTLTrainStepTwoParams)
  )

  override def resultToDocument(result: CVTLTrainResult): Document = BsonDocument(
    "stepOne" -> cvTLTrainStepResultToDocument(result.stepOne),
    "stepTwo" -> result.stepTwo.map(cvTLTrainStepResultToDocument)
  )

  override def documentToResult(document: Document): CVTLTrainResult = CVTLTrainResult(
    stepOne = documentToCVTLTrainStepResult(document.getChildMandatory("stepOne")),
    stepTwo = document.getChild("stepTwo").map(documentToCVTLTrainStepResult)
  )

  private def feParamsToDocument(feParams: FeatureExtractorParams): Document = feParams match {
    case FeatureExtractorParams.CreateNewFeatureExtractorParams(architecture, pipelineParams) => BsonDocument(
      "feParamType" -> BsonString("feArchitecture"),
      "featureExtractorArchitecture" -> BsonString(architecture),
      "pipelineParams" -> BsonDocument(pipelineParams.mapValues(paramToValue))
    )
    case FeatureExtractorParams.UseExistingFeatureExtractorParams(modelId, tuneFE) => BsonDocument(
      "feParamType" -> BsonString("feIdAndTuneParam"),
      "featureExtractorModelId" -> BsonString(modelId),
      "tuneFeatureExtractor" -> BsonBoolean(tuneFE)
    )
  }

  private def cvTLTrainStepOneParamsToDocument(params: CVTLTrainStep1Params): Document = BsonDocument(
    "feParams" -> feParamsToDocument(params.feParams),
    "modelType" -> BsonDocument(CVModelDao.tlConsumerToDocument(params.modelType)),
    "modelParams" -> BsonDocument(params.modelParams.mapValues(paramToValue)),
    "inputAlbumId" -> BsonString(params.inputAlbumId),
    "testInputAlbumId" -> params.testInputAlbumId.map(id => BsonString(id)),
    "automatedAugmentationParams" -> params.automatedAugmentationParams.map(
      AugmentationSerializers.automatedAugmentationParamsToDocument
    ),
    "trainParams" -> params.trainParams.map(trainParamsToDocument)
  )

  private def cVTLTrainStepTwoParamsToDocument(params: CVTLTrainStep2Params): Document =
    BsonDocument(
      "tuneFeatureExtractor" -> BsonBoolean(params.tuneFeatureExtractor),
      "modelType" -> CVModelDao.tlConsumerToDocument(params.modelType),
      "modelParams" -> BsonDocument(params.modelParams.mapValues(paramToValue)),
      "inputAlbumId" -> BsonString(params.inputAlbumId),
      "testInputAlbumid" -> params.testInputAlbumId.map(id => BsonString(id)),
      "automatedAugmentationParams" -> params.automatedAugmentationParams.map(
        AugmentationSerializers.automatedAugmentationParamsToDocument
      ),
      "trainParams" -> params.trainParams.map(trainParamsToDocument)
    )

  private def paramToValue(param: PipelineParam): BsonValue = {
    param match {
      case StringParam(x) => BsonString(x)
      case IntParam(x) => BsonInt32(x)
      case FloatParam(x) => BsonDouble(x)
      case BooleanParam(x) => BsonBoolean(x)
      case StringParams(x) => BsonArray(x.map(BsonString(_)))
      case IntParams(x) => BsonArray(x.map(BsonInt32(_)))
      case FloatParams(x) => BsonArray(x.map(BsonDouble(_)))
      case BooleanParams(x) => BsonArray(x.map(BsonBoolean(_)))
      case EmptySeqParam => BsonArray()
    }
  }

  private def documentToCVTLTrainStepOneParams(document: Document) = CVTLTrainStep1Params(
    feParams = documentToFEParams(document.getChildMandatory("feParams")),
    modelType = CVModelDao.documentToTLConsumer(document.getChildMandatory("modelType")),
    modelParams = document.getChildMandatory("modelParams")
      .map {
        case (key, value) => key -> valueToParam(value)
      }.toMap,
    inputAlbumId = document.getMandatory[BsonString]("inputAlbumId").getValue,
    testInputAlbumId = document.get[BsonString]("testInputAlbumId").map(_.getValue),
    automatedAugmentationParams = document.getChild("automatedAugmentationParams").map(
      AugmentationSerializers.documentToAutomatedAugmentationParams
    ),
    trainParams = document.getChild("trainParams").map(documentToTrainParams)
  )

  private def documentToFEParams(document: Document): FeatureExtractorParams = {
    document.getMandatory[BsonString]("feParamType").getValue match {
      case "feArchitecture" =>
        FeatureExtractorParams.CreateNewFeatureExtractorParams(
          document.getMandatory[BsonString]("featureExtractorArchitecture").getValue,
          document.getChildMandatory("pipelineParams").map { case (key, value) =>
            key -> valueToParam(value)
          }.toMap
        )
      case "feIdAndTuneParam" =>
        FeatureExtractorParams.UseExistingFeatureExtractorParams(
          document.getMandatory[BsonString]("featureExtractorModelId").getValue,
          document.getMandatory[BsonBoolean]("tuneFeatureExtractor").getValue
        )
    }
  }

  private def valueToParam(bsonValue: BsonValue): PipelineParam = {

    def parseMultipleParams(array: BsonArray): PipelineParam = {
      if (array.isEmpty) {
        EmptySeqParam
      } else {
        array.get(0) match {
          case _: BsonString => StringParams(array.map(_.asString.getValue))
          case _: BsonInt32 => IntParams(array.map(_.asInt32.getValue))
          case _: BsonDouble => FloatParams(array.map(_.asDouble.getValue.toFloat))
          case _: BsonBoolean => BooleanParams(array.map(_.asBoolean.getValue))
        }
      }
    }

    bsonValue match {
      case value: BsonString => StringParam(value.getValue)
      case value: BsonInt32 => IntParam(value.getValue)
      case value: BsonDouble => FloatParam(value.getValue.toFloat)
      case value: BsonBoolean => BooleanParam(value.getValue)
      case values: BsonArray  => parseMultipleParams(values)
    }
  }

  private def documentToCVTLTrainStepTwoParams(document: Document): CVTLTrainStep2Params = CVTLTrainStep2Params(
    tuneFeatureExtractor = document.getMandatory[BsonBoolean]("tuneFeatureExtractor").getValue,
    modelType = CVModelDao.documentToTLConsumer(document.getChildMandatory("modelType")),
    modelParams = document.getChildMandatory("modelParams")
      .map {
        case (key, value) => key -> valueToParam(value)
      }.toMap,
    inputAlbumId = document.getMandatory[BsonString]("inputAlbumId").getValue,
    testInputAlbumId = document.get[BsonString]("testInputAlbumId").map(_.getValue),
    automatedAugmentationParams = document.getChild("automatedAugmentationParams").map(
      AugmentationSerializers.documentToAutomatedAugmentationParams
    ),
    trainParams = document.getChild("trainParams").map(documentToTrainParams)
  )

  private def cvTLTrainStepResultToDocument(stepResult: CVTLTrainStepResult): Document = BsonDocument(
    "modelId" -> BsonString(stepResult.modelId),
    "outputAlbumId" -> stepResult.outputAlbumId.map(BsonString(_)),
    "summary" -> stepResult.summary.map(summaryToDocument),
    "testOutputAlbumId" -> stepResult.testOutputAlbumId.map(id => BsonString(id)),
    "autoAugmentationSampleAlbumId" -> stepResult.autoAugmentationSampleAlbumId.map(id => BsonString(id)),
    "testSummary" -> stepResult.testSummary.map(summaryToDocument),
    "augmentationSummary" -> stepResult.augmentationSummary.map(_.map(summaryCell =>
      AugmentationSerializers.augmentationSummaryCellToDocument(summaryCell)
    )),
    "trainTimeSpentSummary" -> stepResult.trainTimeSpentSummary.map(trainTimeSpentSummaryToDocument),
    "evaluateTimeSpentSummary" -> stepResult.evaluateTimeSpentSummary.map(
      CVModelPipelineSerializer.evaluationTimeSpentSummaryToDocument
    ),
    "probabilityPredictionTableId" -> stepResult.probabilityPredictionTableId.map(BsonString(_)),
    "testProbabilityPredictionTableId" -> stepResult.testProbabilityPredictionTableId.map(BsonString(_))
  )

  private def summaryToDocument(summary: CVModelSummary) = BsonDocument(
    "labels" -> summary.labels,
    "confusionMatrix" -> summary.confusionMatrix.map(_.map(row =>
      CommonSerializers.confusionMatrixCellToDocument(row)
    )),
    "mAP" -> summary.mAP.map(BsonDouble(_)),
    "reconstructionLoss" -> summary.reconstructionLoss.map(BsonDouble(_))
  )

  private def trainTimeSpentSummaryToDocument(summary: CVModelTrainTimeSpentSummary): BsonDocument = BsonDocument(
    "dataFetchTime" -> BsonInt64(summary.dataFetchTime),
    "trainingTime" -> BsonInt64(summary.trainingTime),
    "saveModelTime" -> BsonInt64(summary.saveModelTime),
    "predictionTime" -> BsonInt64(summary.predictionTime),
    "tasksQueuedTime" -> BsonInt64(summary.tasksQueuedTime),
    "totalJobTime" -> BsonInt64(summary.totalJobTime),
    "pipelineTimings" -> BsonArray(summary.pipelineTimings.map(CommonSerializers.pipelineTimingToDocument))
  )

  private def documentToCVTLTrainStepResult(document: Document): CVTLTrainStepResult = CVTLTrainStepResult(
    modelId = document.getMandatory[BsonString]("modelId").getValue,
    summary = document.getChild("summary").map(documentToSummary),
    outputAlbumId = document.get[BsonString]("outputAlbumId").map(_.getValue),
    testOutputAlbumId = document.get[BsonString]("testOutputAlbumId").map(_.getValue),
    autoAugmentationSampleAlbumId = document.get[BsonString]("autoAugmentationSampleAlbumId").map(_.getValue),
    testSummary = document.getChild("testSummary").map(documentToSummary),
    augmentationSummary = document.get[BsonArray]("augmentationSummary").map(
      _.map(augmentationCell =>
        AugmentationSerializers.documentToAugmentationSummaryCell(augmentationCell.asDocument)
      )
    ),
    trainTimeSpentSummary = document.getChild("trainTimeSpentSummary").map(
      documentToTrainTimeSpentSummary
    ),
    evaluateTimeSpentSummary = document.getChild("evaluateTimeSpentSummary").map(
      CVModelPipelineSerializer.documentToEvaluationTimeSpentSummary
    ),
    probabilityPredictionTableId = document.get[BsonString]("probabilityPredictionTableId").map(_.getValue),
    testProbabilityPredictionTableId = document.get[BsonString]("testProbabilityPredictionTableId").map(_.getValue)
  )

  private def documentToSummary(document: Document): CVModelSummary = CVModelSummary(
    labels = document.getMandatory[BsonArray]("labels").map(_.asString.getValue),
    confusionMatrix = document.get[BsonArray]("confusionMatrix").map(_.map { matrixRow =>
      CommonSerializers.documentToConfusionMatrixCell(matrixRow.asDocument)
    }),
    mAP = document.get[BsonDouble]("mAP").map(_.getValue),
    reconstructionLoss = document.get[BsonDouble]("reconstructionLoss").map(_.getValue)
  )

  private def documentToTrainTimeSpentSummary(document: Document) = CVModelTrainTimeSpentSummary(
    dataFetchTime = document.getMandatory[BsonInt64]("dataFetchTime").getValue,
    trainingTime = document.getMandatory[BsonInt64]("trainingTime").getValue,
    saveModelTime = document.getMandatory[BsonInt64]("saveModelTime").getValue,
    predictionTime = document.getMandatory[BsonInt64]("predictionTime").getValue,
    tasksQueuedTime = document.getMandatory[BsonInt64]("tasksQueuedTime").getValue,
    totalJobTime = document.getMandatory[BsonInt64]("totalJobTime").getValue,
    pipelineTimings = document.getMandatory[BsonArray]("pipelineTimings").map { elem =>
      CommonSerializers.documentToPipelineTiming(elem.asDocument)
    }.toList
  )

  private def trainParamsToDocument(trainParams: CommonTrainParams): BsonDocument = BsonDocument(
    "inputSize" -> trainParams.inputSize.map(size =>
      BsonDocument(
        "width" -> BsonInt32(size.width),
        "height" -> BsonInt32(size.height)
      )
    ),
    "loi" -> trainParams.loi.map(_.map(labelOfInterest =>
      BsonDocument(
        "label" -> BsonString(labelOfInterest.label),
        "threshold" -> BsonDouble(labelOfInterest.threshold)
      )
    )),
    "defaultVisualThreshold" -> trainParams.defaultVisualThreshold.map(BsonDouble(_)),
    "iouThreshold" -> trainParams.iouThreshold.map(BsonDouble(_)),
    "featureExtractorLearningRate" -> trainParams.featureExtractorLearningRate.map(BsonDouble(_)),
    "modelLearningRate" -> trainParams.modelLearningRate.map(BsonDouble(_))
  )

  private def documentToTrainParams(document: Document): CommonTrainParams = CommonTrainParams(
    inputSize = document.getChild("inputSize").map(inputSizeDocument =>
      InputSize(
        width = inputSizeDocument.getMandatory[BsonInt32]("width").getValue,
        height = inputSizeDocument.getMandatory[BsonInt32]("height").getValue
      )
    ),
    loi = document.get[BsonArray]("loi").map(_.map { labelOfInterest =>
      val labelOfInterestDocument = Document(labelOfInterest.asDocument)
      LabelOfInterest(
        label = labelOfInterestDocument.getMandatory[BsonString]("label").getValue,
        threshold = labelOfInterestDocument.getMandatory[BsonDouble]("threshold").getValue
      )
    }),
    defaultVisualThreshold = document.get[BsonDouble]("defaultVisualThreshold").map(_.getValue.toFloat),
    iouThreshold = document.get[BsonDouble]("iouThreshold").map(_.getValue.toFloat),
    featureExtractorLearningRate = document.get[BsonDouble]("featureExtractorLearningRate").map(_.getValue),
    modelLearningRate = document.get[BsonDouble]("modelLearningRate").map(_.getValue)
  )

}

object CVModelPipelineSerializer {

  case class OperatorIdIs(operatorId: String) extends Filter

  private[dao] def evaluationTimeSpentSummaryToDocument(summary: EvaluateTimeSpentSummary): BsonDocument = BsonDocument(
    "dataFetchTime" -> BsonInt64(summary.dataFetchTime),
    "loadModelTime" -> BsonInt64(summary.loadModelTime),
    "scoreTime" -> BsonInt64(summary.scoreTime),
    "tasksQueuedTime" -> BsonInt64(summary.tasksQueuedTime),
    "totalJobTime" -> BsonInt64(summary.totalJobTime),
    "pipelineTimings" -> BsonArray(summary.pipelineTimings.map(CommonSerializers.pipelineTimingToDocument))
  )

  private[dao] def documentToEvaluationTimeSpentSummary(document: Document): EvaluateTimeSpentSummary =
    EvaluateTimeSpentSummary(
      dataFetchTime = document.getMandatory[BsonInt64]("dataFetchTime").getValue,
      loadModelTime = document.getMandatory[BsonInt64]("loadModelTime").getValue,
      scoreTime = document.getMandatory[BsonInt64]("scoreTime").getValue,
      tasksQueuedTime = document.getMandatory[BsonInt64]("tasksQueuedTime").getValue,
      totalJobTime = document.getMandatory[BsonInt64]("totalJobTime").getValue,
      pipelineTimings = document.getMandatory[BsonArray]("pipelineTimings").map { elem =>
        CommonSerializers.documentToPipelineTiming(elem.asDocument)
      }.toList
    )

}
