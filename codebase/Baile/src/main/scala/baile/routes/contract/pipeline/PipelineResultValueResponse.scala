package baile.routes.contract.pipeline

import baile.domain.pipeline.result.PipelineResultValue
import baile.domain.pipeline.result.PipelineResultValue.{ BooleanValue, FloatValue, IntValue, StringValue }
import baile.utils.json.CommonFormats
import play.api.libs.json._

sealed trait PipelineResultValueResponse

object PipelineResultValueResponse {

  case class IntResultValue(value: Int) extends PipelineResultValueResponse
  case class FloatResultValue(value: Float) extends PipelineResultValueResponse
  case class StringResultValue(value: String) extends PipelineResultValueResponse
  case class BooleanResultValue(value: Boolean) extends PipelineResultValueResponse

  implicit val PipelineResultValueResponseWrites: Writes[PipelineResultValueResponse] =
    new Writes[PipelineResultValueResponse] {
      override def writes(o: PipelineResultValueResponse): JsValue = o match {
        case StringResultValue(value) => JsString(value)
        case IntResultValue(value) => JsNumber(value)
        case FloatResultValue(value) => CommonFormats.FloatWrites.writes(value)
        case BooleanResultValue(value) => JsBoolean(value)
      }
    }

  def fromDomain(in: PipelineResultValue): PipelineResultValueResponse = in match {
    case IntValue(value) => IntResultValue(value)
    case FloatValue(value) => FloatResultValue(value)
    case StringValue(value) => StringResultValue(value)
    case BooleanValue(value) => BooleanResultValue(value)
  }

}
