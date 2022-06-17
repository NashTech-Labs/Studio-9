package cortex.task.column

import play.api.libs.json._

sealed trait ColumnVariableType {
  val stringValue: String
}

object ColumnVariableType {

  case object CONTINUOUS extends ColumnVariableType {
    override val stringValue: String = "CONTINUOUS"
  }

  case object CATEGORICAL extends ColumnVariableType {
    override val stringValue: String = "CATEGORICAL"
  }

  implicit object ColumnVariableTypeReads extends Reads[ColumnVariableType] {
    override def reads(json: JsValue): JsResult[ColumnVariableType] = json match {
      case JsString(ColumnVariableType.CONTINUOUS.stringValue) => JsSuccess(ColumnVariableType.CONTINUOUS)
      case JsString(ColumnVariableType.CATEGORICAL.stringValue) => JsSuccess(ColumnVariableType.CATEGORICAL)
      case _ => JsError(s"Invalid ColumnVariableType: $json")
    }
  }

  implicit object ColumnVariableTypeWrites extends Writes[ColumnVariableType] {
    override def writes(value: ColumnVariableType): JsValue = JsString(value.stringValue)
  }

}
