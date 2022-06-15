package sqlserver.bootstrap

import java.sql.Connection

import com.typesafe.config.Config
import sqlserver.services.db.connectionpool.{ ConnectionProvider, JDBCConnectionProvider }

import scala.util.Try

class TabularConnectionInstantiator(dbConfig: Config) {

  private val driver: String = dbConfig.getString("jdbc-driver")
  private val hostname: String = dbConfig.getString("host")
  private val port: String = dbConfig.getString("port")
  private val database: String = dbConfig.getString("database")
  private val username: String = dbConfig.getString("username")
  private val password: String = dbConfig.getString("password")

  private val url: String = s"jdbc:$driver://$hostname:$port/$database"

  lazy val connectionProvider: ConnectionProvider = new JDBCConnectionProvider(url, username, password)

  def close(connection: Connection): Try[Unit] =
    Try(connection.close())

}
