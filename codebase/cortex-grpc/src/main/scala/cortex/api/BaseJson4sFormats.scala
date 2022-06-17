package cortex.api

import java.time.ZonedDateTime

import org.json4s.{CustomSerializer, DefaultFormats, Formats}
import org.json4s.JsonAST.JString

object BaseJson4sFormats {
  private object ZonedDateTimeFormats extends CustomSerializer[ZonedDateTime](_ => (
    {
      case JString(str) => ZonedDateTime.parse(str)
    },
    {
      case x: ZonedDateTime => JString(x.toString)
    }
  ))

  val baseFormats = DefaultFormats + ZonedDateTimeFormats

  def extend(customSerializer: CustomSerializer[_]*): Formats = {
    baseFormats ++ customSerializer
  }
}
