package cortex.task

import cortex.JsonSupport.SnakeJson
import play.api.libs.json.{ JsValue, Writes }

sealed trait HyperParam

case class DoubleHyperParam(value: Double) extends HyperParam

case class IntHyperParam(value: Int) extends HyperParam

object HyperParam {
  implicit object HyperParamWrites extends Writes[HyperParam] {
    override def writes(hp: HyperParam): JsValue = hp match {
      case doubleHyperParam: DoubleHyperParam => SnakeJson.toJson(doubleHyperParam.value)
      case intHyperParam: IntHyperParam       => SnakeJson.toJson(intHyperParam.value)
    }
  }
}
