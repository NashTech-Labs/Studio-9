package baile.bootstrap

import baile.services.db.connectionpool.{ ConnectionProvider, ScalikeConnectionProvider }
import com.typesafe.config.Config
import scalikejdbc.ConnectionPoolSettings

import scala.util.Try

class TabularConnectionInstantiator(dbConfig: Config) {

  private val driver: String = dbConfig.getString("jdbc-driver")
  private val hostname: String = dbConfig.getString("host")
  private val port: String = dbConfig.getString("port")
  private val database: String = dbConfig.getString("database")
  private val username: String = dbConfig.getString("username")
  private val password: String = dbConfig.getString("password")

  private val url: String = s"jdbc:$driver://$hostname:$port/$database"

  private val connectionPoolConfig = dbConfig.getConfig("connection-pool")

  private val poolName: String = connectionPoolConfig.getString("pool-name")
  private val initialSize: Int = connectionPoolConfig.getInt("initial-size")
  private val maxSize: Int = connectionPoolConfig.getInt("max-size")
  private val connectionTimeout: Long = connectionPoolConfig.getLong("connection-timeout")
  private val driverName: String = connectionPoolConfig.getString("driver-name")

  lazy val connectionProvider: ConnectionProvider = new ScalikeConnectionProvider(poolName)

  val cpSettings: ConnectionPoolSettings = ConnectionPoolSettings(
    initialSize = initialSize,
    maxSize = maxSize,
    connectionTimeoutMillis = connectionTimeout,
    driverName = driverName,
    validationQuery = "SELECT 1"
  )
  Try(scalikejdbc.ConnectionPool.add(Symbol(poolName), url, username, password, cpSettings)).getOrElse {
    throw new RuntimeException(s"Unable to instantiate the connection pool with name $poolName")
  }

  def close: Try[Unit] = {
    Try(scalikejdbc.ConnectionPool.get(Symbol(poolName))).map(_.close())
  }

}
