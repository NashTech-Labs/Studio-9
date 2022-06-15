package cortex.api

import java.util.concurrent.TimeUnit

import play.api.libs.json.Reads.LongReads
import play.api.libs.json.Writes.LongWrites
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.concurrent.duration.{ FiniteDuration, _ }

// TODO remove this once common repo with utils like this will be introduced
package object gemini {

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
