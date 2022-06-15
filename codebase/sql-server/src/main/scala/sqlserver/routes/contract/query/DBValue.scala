package sqlserver.routes.contract.query

import play.api.libs.json._
import sqlserver.domain.table.{ DBValue => DomainDBValue }

trait DBValue

object DBValue {

  case class DBStringValue(value: String) extends DBValue
  case class DBIntValue(value: Int) extends DBValue
  case class DBDoubleValue(value: Double) extends DBValue
  case class DBBooleanValue(value: Boolean) extends DBValue

  def toDomain(dbValue: DBValue): DomainDBValue =
    dbValue match {
      case DBDoubleValue(value) => DomainDBValue.DBDoubleValue(value)
      case DBIntValue(value) => DomainDBValue.DBIntValue(value)
      case DBStringValue(value) => DomainDBValue.DBStringValue(value)
      case DBBooleanValue(value) => DomainDBValue.DBBooleanValue(value)
    }

  implicit val DBValueReads: Reads[DBValue] = new Reads[DBValue] {
    override def reads(json: JsValue): JsResult[DBValue] =
      json match {
        case JsBoolean(value) => JsSuccess(DBBooleanValue(value))
        case JsNumber(value) if value.isValidInt => JsSuccess(DBIntValue(value.toInt))
        case JsNumber(value) => JsSuccess(DBDoubleValue(value.toDouble))
        case JsString(value) => JsSuccess(DBStringValue(value))
        case _ => JsError("Expected json string|number[int or float]|bool")
      }
  }

}
