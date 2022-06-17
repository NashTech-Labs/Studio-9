package cortex.task

import cortex.JsonSupport.SnakeJson
import cortex.task.TabularAccessParams.TabularAccessDBType
import play.api.libs.json.{ JsObject, JsString, JsValue, Writes }

sealed trait TabularAccessParams {
  val dbType: TabularAccessDBType
  val username: String
  val password: String
}

object TabularAccessParams {
  case class RedshiftAccessParams(
      hostname:  String,
      port:      Int,
      username:  String,
      password:  String,
      database:  String,
      s3IamRole: String
  ) extends TabularAccessParams {
    override val dbType: TabularAccessDBType = TabularAccessDBType.Redshift
  }

  case class DremioAccessParams(
      url:         String,
      username:    String,
      password:    String,
      source:      String,
      namespace:   String,
      s3TablesDir: String
  ) extends TabularAccessParams {
    override val dbType: TabularAccessDBType = TabularAccessDBType.Dremio
  }

  case class PostgreSQLAccessParams(
      hostname: String,
      port:     Int,
      username: String,
      password: String,
      database: String
  ) extends TabularAccessParams {
    override val dbType: TabularAccessDBType = TabularAccessDBType.PostgreSQL
  }

  sealed trait TabularAccessDBType {
    val stringValue: String
  }

  object TabularAccessDBType {
    case object Redshift extends TabularAccessDBType {
      override val stringValue: String = "REDSHIFT"
    }
    case object Dremio extends TabularAccessDBType {
      override val stringValue: String = "DREMIO"
    }
    case object PostgreSQL extends TabularAccessDBType {
      override val stringValue: String = "POSTGRES"
    }
  }

  implicit object TabularAccessParamsWrites extends Writes[TabularAccessParams] {
    private implicit object TabularAccessDBTypeWrites extends Writes[TabularAccessDBType] {
      override def writes(tadbt: TabularAccessDBType): JsValue = SnakeJson.toJson(tadbt.stringValue)
    }
    private val redshiftAccessParamsWrites = SnakeJson.writes[RedshiftAccessParams]
    private val dremioAccessParamsWrites = SnakeJson.writes[DremioAccessParams]
    private val postgreSQLAccessParamsWrites = SnakeJson.writes[PostgreSQLAccessParams]

    override def writes(tap: TabularAccessParams): JsValue = (tap match {
      case redshiftAccessParams: RedshiftAccessParams =>
        SnakeJson.toJsObject(redshiftAccessParams)(redshiftAccessParamsWrites)
      case dremioAccessParams: DremioAccessParams =>
        SnakeJson.toJsObject(dremioAccessParams)(dremioAccessParamsWrites)
      case postgreSQLAccessParams: PostgreSQLAccessParams =>
        SnakeJson.toJsObject(postgreSQLAccessParams)(postgreSQLAccessParamsWrites)
    }) ++ JsObject(Seq("db_type" -> JsString(tap.dbType.stringValue)))
  }
}
