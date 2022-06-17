package gemini.utils.json

import java.util.concurrent.TimeUnit

import play.api.libs.json._
import play.api.libs.functional.syntax._

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.Try

object CommonFormats {

  implicit val DurationFormat: Format[Duration] = new Format[Duration] {

    override def reads(json: JsValue): JsResult[Duration] = json match {
      case JsString(string) =>
        Try(Duration(string)).fold(ex => JsError(ex.getMessage), JsSuccess(_))
      case _ =>
        JsError("Expected json string for duration")
    }

    override def writes(duration: Duration): JsValue =
      JsString(duration.toString)

  }

  implicit val FloatWrites: Writes[Float] = new Writes[Float] {
    override def writes(x: Float): JsNumber =
      JsNumber(BigDecimal.decimal(x).round(new java.math.MathContext(4)))
  }

  implicit val TimeUnitFormat: Format[TimeUnit] = new Format[TimeUnit] {

    override def reads(json: JsValue): JsResult[TimeUnit] =
      json match {
        case JsString(str) =>
          TimeUnit.values
            .find(_.name.equalsIgnoreCase(str))
            .map(JsSuccess(_))
            .getOrElse(JsError(s"Unknown time unit value: $str"))
        case _ =>
          JsError("Expected json string for unit time")
      }

    override def writes(timeUnit: TimeUnit): JsValue =
      JsString(timeUnit.toString)

  }

  implicit val FiniteDurationFormat: Format[FiniteDuration] = (
    (__ \ "length").format[Long] and
      (__ \ "unit").format[TimeUnit]
  )(
    (length, unit) => FiniteDuration(length, unit),
    finiteDuration => (finiteDuration.length, finiteDuration.unit)
  )

}
