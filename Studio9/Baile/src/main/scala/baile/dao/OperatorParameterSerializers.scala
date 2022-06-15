package baile.dao

import baile.dao.mongo.BsonHelpers._
import baile.domain.pipeline._
import org.mongodb.scala.Document
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonDocument, BsonDouble, BsonInt32, BsonString }

private[dao] object OperatorParameterSerializers {

  def paramsToDocument(operatorParameter: OperatorParameter): BsonDocument = BsonDocument(
    "name" -> BsonString(operatorParameter.name),
    "description" -> operatorParameter.description.map(BsonString(_)),
    "multiple" -> BsonBoolean(operatorParameter.multiple),
    "typeInfo" -> parameterTypeInfoToDocument(operatorParameter.typeInfo),
    "conditions" -> parameterConditionsToDocument(operatorParameter.conditions)
  )

  def documentToOperatorParameter(document: Document): OperatorParameter = OperatorParameter(
    name = document.getMandatory[BsonString]("name").getValue,
    description = document.get[BsonString]("description").map(_.getValue),
    multiple = document.getMandatory[BsonBoolean]("multiple").getValue,
    typeInfo = documentToParameterTypeInfo(document.getChildMandatory("typeInfo")),
    conditions = documentToParameterConditions(document.getChildMandatory("conditions"))
  )

  private def parameterConditionsToDocument(conditions: Map[String, ParameterCondition]): Document =
    Document(conditions.mapValues(parameterConditionToDocument))

  private def parameterConditionToDocument(parameterCondition: ParameterCondition): Document = {
    parameterCondition match {
      case booleanParameterCondition: BooleanParameterCondition => Document(
        "value" -> BsonBoolean(booleanParameterCondition.value),
        "dataType" -> BsonString("BOOLEAN")
      )
      case stringParameterCondition: StringParameterCondition => Document(
        "values" -> BsonArray(stringParameterCondition.values.map(BsonString(_))),
        "dataType" -> BsonString("STRING")
      )
      case intParameterCondition: IntParameterCondition => Document(
        "values" -> BsonArray(intParameterCondition.values.map(BsonInt32(_))),
        "min" -> intParameterCondition.min.map(BsonInt32(_)),
        "max" -> intParameterCondition.max.map(BsonInt32(_)),
        "dataType" -> BsonString("INTEGER")
      )
      case floatParameterCondition: FloatParameterCondition => Document(
        "values" -> BsonArray(floatParameterCondition.values.map(BsonDouble(_))),
        "min" -> floatParameterCondition.min.map(BsonDouble(_)),
        "max" -> floatParameterCondition.max.map(BsonDouble(_)),
        "dataType" -> BsonString("FLOAT")
      )
    }
  }

  private def documentToParameterConditions(document: Document): Map[String, ParameterCondition] =
    document.toMap.mapValues {
      case d: BsonDocument => documentToParameterCondition(d)
      case unknown => throw new RuntimeException(
        s"Unexpected parameter condition value $unknown. Type ${ unknown.getClass }"
      )
    }

  private def documentToParameterCondition(document: Document): ParameterCondition = {
    val parameterType = document.getMandatory[BsonString]("dataType").getValue
    parameterType match {
      case "FLOAT" => documentToFloatParameterCondition(document)
      case "INTEGER" => documentToIntParameterCondition(document)
      case "BOOLEAN" => documentToBooleanParameterCondition(document)
      case "STRING" => documentToStringParameterCondition(document)
    }
  }

  private def documentToBooleanParameterCondition(document: Document) = BooleanParameterCondition(
    value = document.getMandatory[BsonBoolean]("value").getValue
  )

  private def documentToStringParameterCondition(document: Document) = StringParameterCondition(
    values = document.getMandatory[BsonArray]("values").map(_.asString().getValue)
  )

  private def documentToFloatParameterCondition(document: Document) = FloatParameterCondition(
    values = document.getMandatory[BsonArray]("values").map(_.asDouble().doubleValue().toFloat),
    min = document.get[BsonDouble]("min").map(_.getValue.toFloat),
    max = document.get[BsonDouble]("max").map(_.getValue.toFloat)
  )

  private def documentToIntParameterCondition(document: Document) = IntParameterCondition(
    values = document.getMandatory[BsonArray]("values").map(_.asInt32().getValue),
    min = document.get[BsonInt32]("min").map(_.getValue),
    max = document.get[BsonInt32]("max").map(_.getValue)
  )

  private def documentToFloatParameterTypeInfo(document: Document) = FloatParameterTypeInfo(
    values = document.getMandatory[BsonArray]("values").map(_.asDouble().doubleValue().toFloat),
    default = document.getMandatory[BsonArray]("default").map(_.asDouble().doubleValue().toFloat),
    min = document.get[BsonDouble]("min").map(_.getValue.toFloat),
    max = document.get[BsonDouble]("max").map(_.getValue.toFloat),
    step = document.get[BsonDouble]("step").map(_.getValue.toFloat)
  )

  private def documentToIntParameterTypeInfo(document: Document) = IntParameterTypeInfo(
    values = document.getMandatory[BsonArray]("values").map(_.asInt32().getValue),
    default = document.getMandatory[BsonArray]("default").map(_.asInt32().getValue),
    min = document.get[BsonInt32]("min").map(_.getValue),
    max = document.get[BsonInt32]("max").map(_.getValue),
    step = document.get[BsonInt32]("step").map(_.getValue)
  )

  private def documentToStringParameterTypeInfo(document: Document) = StringParameterTypeInfo(
    values = document.getMandatory[BsonArray]("values").map(_.asString().getValue),
    default = document.getMandatory[BsonArray]("default").map(_.asString().getValue)
  )

  private def documentToBooleanParameterTypeInfo(document: Document) = BooleanParameterTypeInfo(
    default = document.getMandatory[BsonArray]("default").map(_.asBoolean().getValue)
  )

  private def documentToAssetParameterTypeInfo(document: Document) = AssetParameterTypeInfo(
    assetType = CommonSerializers.assetTypeFromString(document.getMandatory[BsonString]("assetType").getValue)
  )

  private def documentToParameterTypeInfo(document: Document): ParameterTypeInfo = {
    val parameterType = document.getMandatory[BsonString]("dataType").getValue
    parameterType match {
      case "FLOAT" => documentToFloatParameterTypeInfo(document)
      case "INTEGER" => documentToIntParameterTypeInfo(document)
      case "BOOLEAN" => documentToBooleanParameterTypeInfo(document)
      case "STRING" => documentToStringParameterTypeInfo(document)
      case "ASSET" => documentToAssetParameterTypeInfo(document)
    }
  }

  private def parameterTypeInfoToDocument(parameter: ParameterTypeInfo): Document = {
    parameter match {
      case booleanParameter: BooleanParameterTypeInfo => Document(
        "default" -> BsonArray(booleanParameter.default.map(BsonBoolean(_))),
        "dataType" -> BsonString("BOOLEAN")
      )
      case stringParameter: StringParameterTypeInfo => Document(
        "values" -> BsonArray(stringParameter.values.map(BsonString(_))),
        "default" -> BsonArray(stringParameter.default.map(BsonString(_))),
        "dataType" -> BsonString("STRING")
      )
      case floatParameter: FloatParameterTypeInfo => Document(
        "values" -> BsonArray(floatParameter.values.map(BsonDouble(_))),
        "default" -> BsonArray(floatParameter.default.map(BsonDouble(_))),
        "min" -> floatParameter.min.map(BsonDouble(_)),
        "max" -> floatParameter.max.map(BsonDouble(_)),
        "step" -> floatParameter.step.map(BsonDouble(_)),
        "dataType" -> BsonString("FLOAT")
      )
      case intParameter: IntParameterTypeInfo => Document(
        "values" -> BsonArray(intParameter.values.map(BsonInt32(_))),
        "default" -> BsonArray(intParameter.default.map(BsonInt32(_))),
        "min" -> intParameter.min.map(BsonInt32(_)),
        "max" -> intParameter.max.map(BsonInt32(_)),
        "step" -> intParameter.step.map(BsonInt32(_)),
        "dataType" -> BsonString("INTEGER")
      )
      case assetParameter: AssetParameterTypeInfo => Document(
        "assetType" -> CommonSerializers.assetTypeToString(assetParameter.assetType),
        "dataType" -> BsonString("ASSET")
      )
    }
  }
}
