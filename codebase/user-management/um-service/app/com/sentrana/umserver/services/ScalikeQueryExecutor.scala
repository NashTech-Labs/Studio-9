package com.sentrana.umserver.services

import java.sql.ResultSet
import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.exceptions.ConfigurationException
import play.api.Configuration
import scalikejdbc.{ ConnectionPool, ConnectionPoolSettings, DB, SQL }

import scala.collection.immutable.ListMap
import scala.util.Try

/**
 * Created by Alexander on 03.07.2016.
 */
@Singleton
class ScalikeQueryExecutor @Inject() (configuration: Configuration) extends QueryExecutor {

  override def executeQuery(connectionPoolName: String)(query: String): Unit = {
    addConnectionPoolIfNotInitialized(connectionPoolName).flatMap{ r =>
      getConnectionPool(connectionPoolName).map { connectionPool =>
        DB(connectionPool.borrow()).autoCommit { implicit session =>
          SQL(query).update().apply
        }
      }
    }
    ()
  }

  override def executeReadOnlyQuery[T, R](connectionPoolName: String)(query: String)(implicit dataExtractor: ResultSet => (T, R)): Try[ListMap[T, R]] = {
    addConnectionPoolIfNotInitialized(connectionPoolName).flatMap{ r =>
      getConnectionPool(connectionPoolName).map { connectionPool =>
        val result = DB(connectionPool.borrow()).readOnly { implicit session =>
          SQL(query).map { rs => dataExtractor(rs.underlying) }.list.apply
        }
        ListMap(result: _*)
      }
    }
  }

  private def addConnectionPoolIfNotInitialized(connectionPoolName: String): Try[Unit] = {
    Try(if (!hasConnectionPool(connectionPoolName)) {
      addConnectionPool(connectionPoolName)
    })
  }

  private[services] def hasConnectionPool(name: String): Boolean = {
    scalikejdbc.ConnectionPool.isInitialized(name)
  }

  private[services] def addConnectionPool(name: String): Unit = {
    val url = getDbConfig(s"db.${name}.url")
    val driver = getDbConfig(s"db.${name}.driver")
    val username = getDbConfig(s"db.${name}.username")
    val password = getDbConfig(s"db.${name}.password")

    addConnectionPool(name, driver, url, username, password)
  }

  private def getDbConfig(propertyName: String): String = {
    configuration.getString(propertyName).getOrElse(throw new ConfigurationException(s"${propertyName} is not defined"))
  }

  private[services] def addConnectionPool(name: String, driver: String, url: String, username: String, password: String): Unit = {
    val cpSettings = ConnectionPoolSettings(driverName = driver)
    scalikejdbc.ConnectionPool.add(name, url, username, password, cpSettings)
  }

  private[services] def getConnectionPool(name: String): Try[ConnectionPool] = {
    Try(scalikejdbc.ConnectionPool.get(name))
  }
}

object DataExtractor {
  implicit def extractData(rs: ResultSet): (String, String) = {
    val valueColumn = getColumnIndex(rs, "value") getOrElse 1
    val displayTextColumn = getColumnIndex(rs, "display_text") getOrElse 2
    (rs.getString(valueColumn), rs.getString(displayTextColumn))
  }

  private def getColumnIndex(resultSet: ResultSet, columnLabel: String): Option[Int] =
    Try(resultSet.findColumn(columnLabel)).toOption
}
