package baile.dao.pipeline

import baile.dao.mongo.BsonHelpers._
import baile.domain.pipeline.PipelineParams._
import baile.domain.pipeline.{ PipelineCoordinates, PipelineOutputReference, PipelineStep }
import org.mongodb.scala.Document
import org.mongodb.scala.bson.{
  BsonArray,
  BsonBoolean,
  BsonDocument,
  BsonDouble,
  BsonInt32,
  BsonString,
  BsonValue
}

import scala.collection.JavaConverters._

private[dao] object PipelineStepSerializer {

  def pipelineStepToDocument(entity: PipelineStep): Document =
    Document(
      "id" -> BsonString(entity.id),
      "operatorId" -> BsonString(entity.operatorId),
      "inputs" -> Document(entity.inputs.mapValues(pipelineOutputReferenceToDocument)),
      "params" -> Document(entity.params.mapValues(paramToValue)),
      "coordinates" -> entity.coordinates.map(pipelineCoordinatesToDocument)
    )

  def documentToPipelineStep(document: Document): PipelineStep = PipelineStep(
    id = document.getMandatory[BsonString]("id").getValue,
    operatorId = document.getMandatory[BsonString]("operatorId").getValue,
    inputs = documentToInputs(document.getChildMandatory("inputs")),
    params = document.getChildMandatory("params").toMap.mapValues(valueToParam),
    coordinates = document.getChild("coordinates").map(documentToPipelineCoordinates)
  )

  private def documentToPipelineOutputReference(document: Document): PipelineOutputReference = PipelineOutputReference(
    stepId = document.getMandatory[BsonString]("stepId").getValue,
    outputIndex = document.getMandatory[BsonInt32]("outputIndex").asInt32().getValue
  )

  private def documentToInputs(document: Document): Map[String, PipelineOutputReference] =
    document.toMap.mapValues {
      case d: BsonDocument => documentToPipelineOutputReference(d)
      case unknown => throw new RuntimeException(
        s"Unexpected output reference $unknown"
      )
    }

  private def documentToPipelineCoordinates(document: Document): PipelineCoordinates = PipelineCoordinates(
    x = document.getMandatory[BsonInt32]("x").getValue,
    y = document.getMandatory[BsonInt32]("y").getValue
  )

  private def pipelineCoordinatesToDocument(pipelineCoordinates: PipelineCoordinates): Document =
    Document(
      "x" -> BsonInt32(pipelineCoordinates.x),
      "y" -> BsonInt32(pipelineCoordinates.y)
    )

  private def pipelineOutputReferenceToDocument(pipelineOutputReference: PipelineOutputReference): Document =
    Document(
      "stepId" -> BsonString(pipelineOutputReference.stepId),
      "outputIndex" -> BsonInt32(pipelineOutputReference.outputIndex)
    )

  private def paramToValue(param: PipelineParam): BsonValue = {
    param match {
      case StringParam(value) => BsonString(value)
      case IntParam(value) => BsonInt32(value)
      case FloatParam(value) => BsonDouble(value)
      case BooleanParam(value) => BsonBoolean(value)
      case StringParams(values) => BsonArray(values.map(BsonString(_)))
      case IntParams(values) => BsonArray(values.map(BsonInt32(_)))
      case FloatParams(values) => BsonArray(values.map(BsonDouble(_)))
      case BooleanParams(values) => BsonArray(values.map(BsonBoolean(_)))
      case EmptySeqParam => BsonArray()
    }
  }

  private def valueToParam(bsonValue: BsonValue): PipelineParam = {
    bsonValue match {
      case value: BsonString => StringParam(value.asString().getValue)
      case value: BsonInt32 => IntParam(value.asInt32().getValue)
      case value: BsonDouble => FloatParam(value.asDouble().getValue.toFloat)
      case value: BsonBoolean => BooleanParam(value.asBoolean().getValue)
      case values: BsonArray => values.getValues.asScala.toList match {
        case value :: _ if value.isString =>
          StringParams(values.map(_.asString().getValue))
        case value :: _ if value.isBoolean =>
          BooleanParams(values.map(_.asBoolean().getValue))
        case value :: _ if value.isDouble =>
          FloatParams(values.map(_.asDouble().getValue.toFloat))
        case value :: _ if value.isInt32 =>
          IntParams(values.map(_.asInt32().getValue))
        case Nil => EmptySeqParam
        case unknown => throw new RuntimeException(s"Unexpected param values $unknown")
      }
      case unknown => throw new RuntimeException(s"Unexpected param value $unknown")
    }
  }

}
