package sqlserver.services.db.connectionpool

import java.sql.{ Connection, DriverManager }

import scala.util.Try

class JDBCConnectionProvider(url: String, username: String, password: String) extends ConnectionProvider {
  override def getConnection: Try[Connection] = Try {
    DriverManager.getConnection(url, username, password)
  }
}
