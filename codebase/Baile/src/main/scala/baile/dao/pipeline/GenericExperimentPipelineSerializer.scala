package baile.dao.pipeline

import baile.dao.CommonSerializers
import baile.dao.experiment.PipelineSerializer
import baile.dao.experiment.SerializerDelegator.HasAssetReference
import baile.dao.mongo.BsonHelpers._
import baile.dao.pipeline.GenericExperimentPipelineSerializer.OperatorIdIn
import baile.daocommons.filters.Filter
import baile.domain.asset.AssetReference
import baile.domain.pipeline.pipeline.GenericExperimentPipeline
import baile.domain.pipeline.result._
import org.mongodb.scala.Document
import org.mongodb.scala.bson._
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters

import scala.util.Try

class GenericExperimentPipelineSerializer
  extends PipelineSerializer[GenericExperimentPipeline, GenericExperimentResult] {

  override val filterMapper: PartialFunction[Filter, Try[Bson]] = {
    case HasAssetReference(assetReference) => Try(Filters.or(
      hasAssetReference("pipeline.assets", assetReference),
      Filters.elemMatch("result.steps", hasAssetReference("assets", assetReference))
    ))
    case OperatorIdIn(operatorIds) => Try(Filters.or(
      Filters.elemMatch("pipeline.steps", Filters.in("operatorId", operatorIds: _*))
    ))
  }

  override def pipelineToDocument(genericExperimentPipeline: GenericExperimentPipeline): Document =
    BsonDocument(
      "steps" -> genericExperimentPipeline.steps.map(PipelineStepSerializer.pipelineStepToDocument),
      "assets" -> genericExperimentPipeline.assets.map(assetReferenceToDocument)
    )

  override def documentToPipeline(document: Document): GenericExperimentPipeline =
    GenericExperimentPipeline(
      steps = document.getMandatory[BsonArray]("steps").map(elem =>
        PipelineStepSerializer.documentToPipelineStep(elem.asDocument)
      ),
      assets = document.getMandatory[BsonArray]("assets").map(elem =>
        documentToAssetReference(elem.asDocument)
      )
    )

  override def resultToDocument(genericExperimentResult: GenericExperimentResult): Document =
    BsonDocument(
      "steps" -> genericExperimentResult.steps.map(pipelineStepResultToDocument)
    )

  override def documentToResult(document: Document): GenericExperimentResult =
    GenericExperimentResult(
      steps = document.getMandatory[BsonArray]("steps").map(elem =>
        documentToPipelineStepResult(elem.asDocument)
      )
    )

  private def pipelineStepResultToDocument(genericExperimentStepResult: GenericExperimentStepResult): Document =
    Document(
      "id" -> BsonString(genericExperimentStepResult.id),
      "assets" -> genericExperimentStepResult.assets.map(assetReferenceToDocument),
      "summaries" -> genericExperimentStepResult.summaries.map(pipelineOperatorApplicationSummaryToDocument),
      "outputValues" -> Document(genericExperimentStepResult.outputValues.map {
        case (k,v) => (k.toString, resultValueToBsonValue(v))
      }),
      "executionTime" -> BsonInt64(genericExperimentStepResult.executionTime),
      "failureMessage" -> genericExperimentStepResult.failureMessage.map(BsonString(_))
    )

  private def documentToPipelineStepResult(document: Document): GenericExperimentStepResult =
    GenericExperimentStepResult(
      id = document.getMandatory[BsonString]("id").getValue,
      assets = document.getMandatory[BsonArray]("assets") map { value =>
        documentToAssetReference(value.asDocument())
      },
      summaries = document.getMandatory[BsonArray]("summaries") map { value =>
        documentToPipelineOperatorApplicationSummary(value.asDocument())
      },
      outputValues = document.getChildMandatory("outputValues").toMap.map {
        case (k,v) => (k.toInt, bsonValueToResultValue(v))
      },
      executionTime = document.getMandatory[BsonInt64]("executionTime").asInt64.getValue,
      failureMessage = document.get[BsonString]("failureMessage").map(_.getValue)
    )

  private def documentToPipelineOperatorApplicationSummary(document: Document): PipelineOperatorApplicationSummary =
    document.getMandatory[BsonString]("_type").getValue match {
      case "ConfusionMatrix" => documentToConfusionMatrix(document)
      case "SimpleSummary" => documentToSimpleSummary(document)
    }

  private def pipelineOperatorApplicationSummaryToDocument(summary: PipelineOperatorApplicationSummary): Document =
    summary match {
      case simpleSummary: SimpleSummary =>
        Document("_type"-> "SimpleSummary") ++ simpleSummaryToDocument(simpleSummary)
      case confusionMatrixSummary: ConfusionMatrix =>
        Document("_type"-> "ConfusionMatrix") ++ confusionMatrixToDocument(confusionMatrixSummary)
    }

  private def documentToConfusionMatrix(document: Document): ConfusionMatrix =
    ConfusionMatrix(
      confusionMatrixCells = document.getMandatory[BsonArray]("confusionMatrixCells").map(matrixRow =>
        CommonSerializers.documentToConfusionMatrixCell(matrixRow.asDocument)
      ),
      labels = document.getMandatory[BsonArray]("labels").map(_.asString.getValue)
    )

  private def confusionMatrixToDocument(summary: ConfusionMatrix) =
    Document(
      "confusionMatrixCells" -> summary.confusionMatrixCells.map(row =>
        CommonSerializers.confusionMatrixCellToDocument(row)
      ),
      "labels" -> summary.labels
    )

  private def documentToSimpleSummary(document: Document): SimpleSummary =
    SimpleSummary(
      values = document.getChildMandatory("values").toMap.mapValues(bsonValueToResultValue)
    )

  private def simpleSummaryToDocument(summary: SimpleSummary): Document =
    Document(
      "values" -> Document(summary.values.mapValues(resultValueToBsonValue))
    )

  private def resultValueToBsonValue(param: PipelineResultValue): BsonValue = {
    import baile.domain.pipeline.result.PipelineResultValue._

    param match {
      case IntValue(value) => BsonInt32(value)
      case FloatValue(value) => BsonDouble(value)
      case StringValue(value) => BsonString(value)
      case BooleanValue(value) => BsonBoolean(value)
    }
  }

  private def bsonValueToResultValue(bsonValue: BsonValue): PipelineResultValue = {
    import baile.domain.pipeline.result.PipelineResultValue._

    bsonValue match {
      case value: BsonInt32 => IntValue(value.getValue)
      case value: BsonDouble => FloatValue(value.getValue.toFloat)
      case value: BsonString => StringValue(value.getValue)
      case value: BsonBoolean => BooleanValue(value.getValue)
      case unknown => throw new RuntimeException(s"Unexpected resultParam value $unknown")
    }
  }

  private def documentToAssetReference(document: Document): AssetReference =
    AssetReference(
      id = document.getMandatory[BsonString]("id").getValue,
      `type` = CommonSerializers.assetTypeFromString(document.getMandatory[BsonString]("type").getValue)
    )

  private def assetReferenceToDocument(assetReference: AssetReference): Document =
    Document(
      "id" -> BsonString(assetReference.id),
      "type" -> BsonString(CommonSerializers.assetTypeToString(assetReference.`type`))
    )

  private def hasAssetReference(fieldName: String, assetReference: AssetReference): Bson = Filters.elemMatch(
    fieldName,
    Filters.and(
      Filters.equal("id", assetReference.id),
      Filters.equal("type", CommonSerializers.assetTypeToString(assetReference.`type`))
    )
  )

}

object GenericExperimentPipelineSerializer {

  case class OperatorIdIn(operatorIds: Seq[String]) extends Filter

}
