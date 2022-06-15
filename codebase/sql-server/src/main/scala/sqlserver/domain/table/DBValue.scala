package sqlserver.domain.table

sealed trait DBValue

object DBValue {

  case class DBStringValue(value: String) extends DBValue
  case class DBIntValue(value: Int) extends DBValue
  case class DBDoubleValue(value: Double) extends DBValue
  case class DBBooleanValue(value: Boolean) extends DBValue

}
