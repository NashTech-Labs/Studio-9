package baile.routes.contract.table

import baile.domain.table.TableRowValue
import play.api.libs.json._

sealed trait ListDatasetValueResponse

object ListDatasetValueResponse {

  case class StringValue(value: String) extends ListDatasetValueResponse

  case class NumericValue(value: Double) extends ListDatasetValueResponse

  case class BooleanValue(value: Boolean) extends ListDatasetValueResponse

  case object NullValue extends ListDatasetValueResponse

  def fromDomain(value: TableRowValue): ListDatasetValueResponse = value match {
    case TableRowValue.StringValue(value) => StringValue(value)
    case TableRowValue.BooleanValue(value) => BooleanValue(value)
    case TableRowValue.IntegerValue(value) => NumericValue(value)
    case TableRowValue.DoubleValue(value) => NumericValue(value)
    case TableRowValue.LongValue(value) => NumericValue(value)
    case TableRowValue.TimestampValue(value) => StringValue(value)
    case TableRowValue.NullValue => NullValue
  }

  implicit val ListDatasetValueResponseWrites: Writes[ListDatasetValueResponse] = new Writes[ListDatasetValueResponse] {
    override def writes(value: ListDatasetValueResponse): JsValue = value match {
      case StringValue(value) => JsString(value)
      case NumericValue(value) => JsNumber(value)
      case BooleanValue(value) => JsNumber(if (value) 1 else 0)
      case NullValue => JsNull
    }
  }

}
