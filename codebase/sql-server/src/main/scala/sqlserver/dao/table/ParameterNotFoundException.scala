package sqlserver.dao.table

case class ParameterNotFoundException(parameterName: String) extends RuntimeException
