package sqlserver.utils.json

import play.api.libs.json.{ JsError, JsString, JsSuccess, Reads }

object EnumReadsBuilder {

  def build[T](stringToValue: PartialFunction[String, T], typeName: String = ""): Reads[T] = Reads[T] { json =>
    val additionalInfo = if (typeName.nonEmpty) s" for type $typeName" else ""
    json match {
      case JsString(string) =>
        stringToValue.lift(string) match {
          case Some(value) => JsSuccess(value)
          case None =>
            JsError(s"Unexpected string value '$string'" + additionalInfo)
        }
      case _ =>
        val additionalInfo =
          if (typeName.nonEmpty) s" for type $typeName" else ""
        JsError("Expected json string" + additionalInfo)
    }
  }

}
