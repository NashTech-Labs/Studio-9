package com.sentrana.umserver

import com.sentrana.umserver.services.QueryExecutor
import org.scalatest.Assertions

/**
  * Created by Alexander on 04.07.2016.
  */
trait WithSQLSupport extends Assertions {
  protected val connectionProvider: QueryExecutor

  protected def connectionName = "sql_db_connection"
  protected def url = s"jdbc:h2:mem:${getClass.toString}"
  protected def driver = "org.h2.Driver"
  protected def username = "sa"
  protected def password = ""

  protected def getValidValuesQuery(tableName: String) = s"select a.value, a.display_text from ${tableName} a"

  protected def createTableQuery(tableName: String, column1Name: String, column2Name: String): String = {
    s"create table ${tableName} (${column1Name} varchar(255) primary key, ${column2Name} varchar(255))"
  }

  protected def insertQuery(tableName: String, column1Name: String, column2Name: String, value1: String, value2: String): String = {
    s"insert into ${tableName} (${column1Name}, ${column2Name}) values('${value1}', '${value2}')"
  }

  protected lazy val sqlConfig = Map(
    s"db.${connectionName}.url" -> url,
    s"db.${connectionName}.driver" -> driver,
    s"db.${connectionName}.username" -> username,
    s"db.${connectionName}.password" -> password
  )

  def executeQuery(connectionPoolName: String, query: String): Unit = {
    connectionProvider.executeQuery(connectionPoolName)(query)
  }
}
