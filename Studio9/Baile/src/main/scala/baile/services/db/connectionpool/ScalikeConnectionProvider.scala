package baile.services.db.connectionpool

import java.sql.Connection

import scala.util.Try

class ScalikeConnectionProvider(poolName: String) extends ConnectionProvider {

  val poolNameSymbol: Symbol = Symbol(poolName)

  override def getConnection: Try[Connection] = Try(scalikejdbc.ConnectionPool.get(poolNameSymbol)).map { pool =>
    val connection = pool.borrow()
    // Required for server-side cursor for some jdbc drivers, like PostgreSQL.
    // As long as Baile does not do any write operations, this setting should be fine for all queries.
    connection.setAutoCommit(false)
    connection
  }

}
