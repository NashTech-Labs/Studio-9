package baile.routes.contract.pipeline.operator

import baile.domain.pipeline._
import baile.utils.CollectionExtensions.seqToOptionalNonEmptySeq
import play.api.libs.json.{ Json, OWrites, Writes }
import baile.utils.json.CommonFormats.FloatWrites

sealed trait ParameterConditionResponse

object ParameterConditionResponse {

  case class FloatParameterConditionResponse(
    values: Option[Seq[Float]],
    min: Option[Float],
    max: Option[Float]
  ) extends ParameterConditionResponse

  case class IntParameterConditionResponse(
    values: Option[Seq[Int]],
    min: Option[Int],
    max: Option[Int]
  ) extends ParameterConditionResponse

  case class BooleanParameterConditionResponse(
    value: Boolean
  ) extends ParameterConditionResponse

  case class StringParameterConditionResponse(
    values: Seq[String]
  ) extends ParameterConditionResponse


  def fromDomain(condition: ParameterCondition): ParameterConditionResponse = {
    condition match {
      case floatParameterCondition: FloatParameterCondition =>
        FloatParameterConditionResponse(
          values = seqToOptionalNonEmptySeq(floatParameterCondition.values),
          min = floatParameterCondition.min,
          max = floatParameterCondition.max
        )

      case intParameterCondition: IntParameterCondition =>
        IntParameterConditionResponse(
          values = seqToOptionalNonEmptySeq(intParameterCondition.values),
          min = intParameterCondition.min,
          max = intParameterCondition.max
        )

      case booleanParameterCondition: BooleanParameterCondition =>
        BooleanParameterConditionResponse(
          value = booleanParameterCondition.value
        )

      case stringParameterCondition: StringParameterCondition =>
        StringParameterConditionResponse(
          values = stringParameterCondition.values
        )
    }
  }


  private implicit val FloatParameterConditionResponseWrites: OWrites[FloatParameterConditionResponse] =
    Json.writes[FloatParameterConditionResponse]
  private implicit val IntParameterConditionResponseWrites: OWrites[IntParameterConditionResponse] =
    Json.writes[IntParameterConditionResponse]
  private implicit val BooleanParameterConditionResponseWrites: OWrites[BooleanParameterConditionResponse] =
    Json.writes[BooleanParameterConditionResponse]
  private implicit val StringParameterConditionResponseWrites: OWrites[StringParameterConditionResponse] =
    Json.writes[StringParameterConditionResponse]

  implicit val ParameterConditionResponseWrites: Writes[ParameterConditionResponse] = {
    case floatParameterCondition: FloatParameterConditionResponse =>
      FloatParameterConditionResponseWrites.writes(floatParameterCondition)
    case booleanParameterCondition: BooleanParameterConditionResponse =>
      BooleanParameterConditionResponseWrites.writes(booleanParameterCondition)
    case stringParameterCondition: StringParameterConditionResponse =>
      StringParameterConditionResponseWrites.writes(stringParameterCondition)
    case integerParameterCondition: IntParameterConditionResponse =>
      IntParameterConditionResponseWrites.writes(integerParameterCondition)
  }

}
