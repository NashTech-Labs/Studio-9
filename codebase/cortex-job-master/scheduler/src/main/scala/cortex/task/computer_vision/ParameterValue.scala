package cortex.task.computer_vision

import cortex.JsonSupport.SnakeJson
import play.api.libs.json.{ JsValue, Writes }

sealed trait ParameterValue

object ParameterValue {

  case class StringValue(value: String) extends ParameterValue
  case class IntValue(value: Int) extends ParameterValue
  case class FloatValue(value: Float) extends ParameterValue
  case class BooleanValue(value: Boolean) extends ParameterValue
  case class StringValues(values: Seq[String]) extends ParameterValue
  case class IntValues(values: Seq[Int]) extends ParameterValue
  case class FloatValues(values: Seq[Float]) extends ParameterValue
  case class BooleanValues(values: Seq[Boolean]) extends ParameterValue

  implicit object ParameterValueWrites extends Writes[ParameterValue] {
    override def writes(o: ParameterValue): JsValue = o match {
      case stringValue: StringValue     => SnakeJson.toJson(stringValue.value)
      case intValue: IntValue           => SnakeJson.toJson(intValue.value)
      case floatValue: FloatValue       => SnakeJson.toJson(floatValue.value)
      case booleanValue: BooleanValue   => SnakeJson.toJson(booleanValue.value)
      case stringValues: StringValues   => SnakeJson.toJson(stringValues.values)
      case intValues: IntValues         => SnakeJson.toJson(intValues.values)
      case floatValues: FloatValues     => SnakeJson.toJson(floatValues.values)
      case booleanValues: BooleanValues => SnakeJson.toJson(booleanValues.values)
    }
  }

}
