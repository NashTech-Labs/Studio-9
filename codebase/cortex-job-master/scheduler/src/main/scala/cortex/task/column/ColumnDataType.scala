package cortex.task.column

import play.api.libs.json._

sealed trait ColumnDataType {
  val stringValue: String
}

object ColumnDataType {

  case object STRING extends ColumnDataType {
    override val stringValue: String = "STRING"
  }

  case object INTEGER extends ColumnDataType {
    override val stringValue: String = "INTEGER"
  }

  case object LONG extends ColumnDataType {
    override val stringValue: String = "LONG"
  }

  case object BOOLEAN extends ColumnDataType {
    override val stringValue: String = "BOOLEAN"
  }

  case object DOUBLE extends ColumnDataType {
    override val stringValue: String = "DOUBLE"
  }

  case object TIMESTAMP extends ColumnDataType {
    override val stringValue: String = "TIMESTAMP"
  }

  implicit object ColumnDataTypeReads extends Reads[ColumnDataType] {
    override def reads(json: JsValue): JsResult[ColumnDataType] = json match {
      case JsString(ColumnDataType.STRING.stringValue) => JsSuccess(ColumnDataType.STRING)
      case JsString(ColumnDataType.INTEGER.stringValue) => JsSuccess(ColumnDataType.INTEGER)
      case JsString(ColumnDataType.LONG.stringValue) => JsSuccess(ColumnDataType.LONG)
      case JsString(ColumnDataType.BOOLEAN.stringValue) => JsSuccess(ColumnDataType.BOOLEAN)
      case JsString(ColumnDataType.DOUBLE.stringValue) => JsSuccess(ColumnDataType.DOUBLE)
      case JsString(ColumnDataType.TIMESTAMP.stringValue) => JsSuccess(ColumnDataType.TIMESTAMP)
      case _ => JsError(s"Invalid ColumnDataType: $json")
    }
  }

  implicit object ColumnDataTypeWrites extends Writes[ColumnDataType] {
    override def writes(value: ColumnDataType): JsValue = JsString(value.stringValue)
  }

}
