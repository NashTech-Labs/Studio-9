package baile.routes.contract.pipeline.operator

import baile.domain.asset.AssetType
import baile.domain.pipeline._
import baile.routes.contract.asset.AssetTypeFormat
import baile.utils.json.CommonFormats.FloatWrites
import baile.utils.CollectionExtensions.{ mapToOptionalNonEmptyMap, seqToOptionalNonEmptySeq }
import play.api.libs.json.{ JsString, Json, OWrites }

sealed trait ParameterDefinitionResponse {
  val name: String
  val description: Option[String]
  val multiple: Boolean
  val conditions: Option[Map[String, ParameterConditionResponse]]
}

object ParameterDefinitionResponse {

  case class BooleanParameterDefinitionResponse(
    override val name: String,
    override val description: Option[String],
    override val multiple: Boolean,
    override val conditions: Option[Map[String, ParameterConditionResponse]],
    defaults: Option[Seq[Boolean]]
  ) extends ParameterDefinitionResponse

  case class FloatParameterDefinitionResponse(
    override val name: String,
    override val description: Option[String],
    override val multiple: Boolean,
    override val conditions: Option[Map[String, ParameterConditionResponse]],
    options: Option[Seq[Float]],
    defaults: Option[Seq[Float]],
    min: Option[Float],
    max: Option[Float],
    step: Option[Float]
  ) extends ParameterDefinitionResponse

  case class IntParameterDefinitionResponse(
    override val name: String,
    override val description: Option[String],
    override val multiple: Boolean,
    override val conditions: Option[Map[String, ParameterConditionResponse]],
    options: Option[Seq[Int]],
    defaults: Option[Seq[Int]],
    min: Option[Int],
    max: Option[Int],
    step: Option[Int]
  ) extends ParameterDefinitionResponse

  case class StringParameterDefinitionResponse(
    override val name: String,
    override val description: Option[String],
    override val multiple: Boolean,
    override val conditions: Option[Map[String, ParameterConditionResponse]],
    options: Seq[String],
    defaults: Option[Seq[String]]
  ) extends ParameterDefinitionResponse

  case class AssetParameterDefinitionResponse(
    override val name: String,
    override val description: Option[String],
    override val multiple: Boolean,
    override val conditions: Option[Map[String, ParameterConditionResponse]],
    assetType: AssetType
  ) extends ParameterDefinitionResponse


  def fromDomain(operatorParameter: OperatorParameter): ParameterDefinitionResponse =
    operatorParameter.typeInfo match {
      case typeInfo: BooleanParameterTypeInfo =>
        BooleanParameterDefinitionResponse(
          name = operatorParameter.name,
          description = operatorParameter.description,
          multiple = operatorParameter.multiple,
          conditions = mapToOptionalNonEmptyMap(
            operatorParameter.conditions.mapValues(ParameterConditionResponse.fromDomain)
          ),
          defaults = seqToOptionalNonEmptySeq(typeInfo.default)
        )

      case typeInfo: FloatParameterTypeInfo =>
        FloatParameterDefinitionResponse(
          name = operatorParameter.name,
          description = operatorParameter.description,
          multiple = operatorParameter.multiple,
          conditions = mapToOptionalNonEmptyMap(
            operatorParameter.conditions.mapValues(ParameterConditionResponse.fromDomain)
          ),
          options = seqToOptionalNonEmptySeq(typeInfo.values),
          defaults = seqToOptionalNonEmptySeq(typeInfo.default),
          min = typeInfo.min,
          max = typeInfo.max,
          step = typeInfo.step
        )

      case typeInfo: IntParameterTypeInfo =>
        IntParameterDefinitionResponse(
          name = operatorParameter.name,
          description = operatorParameter.description,
          multiple = operatorParameter.multiple,
          conditions = mapToOptionalNonEmptyMap(
            operatorParameter.conditions.mapValues(ParameterConditionResponse.fromDomain)
          ),
          options = seqToOptionalNonEmptySeq(typeInfo.values),
          defaults = seqToOptionalNonEmptySeq(typeInfo.default),
          min = typeInfo.min,
          max = typeInfo.max,
          step = typeInfo.step
        )

      case typeInfo: StringParameterTypeInfo =>
        StringParameterDefinitionResponse(
          name = operatorParameter.name,
          description = operatorParameter.description,
          multiple = operatorParameter.multiple,
          conditions = mapToOptionalNonEmptyMap(
            operatorParameter.conditions.mapValues(ParameterConditionResponse.fromDomain)
          ),
          options = typeInfo.values,
          defaults = seqToOptionalNonEmptySeq(typeInfo.default)
        )

      case typeInfo: AssetParameterTypeInfo =>
        AssetParameterDefinitionResponse(
          name = operatorParameter.name,
          description = operatorParameter.description,
          multiple = operatorParameter.multiple,
          conditions = mapToOptionalNonEmptyMap(
            operatorParameter.conditions.mapValues(ParameterConditionResponse.fromDomain)
          ),
          assetType = typeInfo.assetType
        )
    }


  private implicit val BooleanParameterDefinitionResponseWrites: OWrites[BooleanParameterDefinitionResponse] =
    Json.writes[BooleanParameterDefinitionResponse]
  private implicit val FloatParameterDefinitionResponseWrites: OWrites[FloatParameterDefinitionResponse] =
    Json.writes[FloatParameterDefinitionResponse]
  private implicit val IntParameterDefinitionResponseWrites: OWrites[IntParameterDefinitionResponse] =
    Json.writes[IntParameterDefinitionResponse]
  private implicit val StringParameterDefinitionResponseWrites: OWrites[StringParameterDefinitionResponse] =
    Json.writes[StringParameterDefinitionResponse]
  private implicit val AssetParameterDefinitionResponseWrites: OWrites[AssetParameterDefinitionResponse] =
    Json.writes[AssetParameterDefinitionResponse]

  implicit val ParameterDefinitionResponseWrites: OWrites[ParameterDefinitionResponse] =
    OWrites(
      (parameter: ParameterDefinitionResponse) => {

        val (baseJson, parameterType) = parameter match {
          case floatParameter: FloatParameterDefinitionResponse =>
            (FloatParameterDefinitionResponseWrites.writes(floatParameter), "float")
          case booleanParameter: BooleanParameterDefinitionResponse =>
            (BooleanParameterDefinitionResponseWrites.writes(booleanParameter), "boolean")
          case stringParameter: StringParameterDefinitionResponse =>
            (StringParameterDefinitionResponseWrites.writes(stringParameter), "string")
          case integerParameter: IntParameterDefinitionResponse =>
            (IntParameterDefinitionResponseWrites.writes(integerParameter), "int")
          case assetParameter: AssetParameterDefinitionResponse =>
            (AssetParameterDefinitionResponseWrites.writes(assetParameter), "assetReference")
        }

        baseJson + ("type" -> JsString(parameterType))
      }
    )

}
