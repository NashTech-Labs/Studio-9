package com.sentrana.umserver.services

import java.sql.ResultSet
import scala.collection.immutable.ListMap
import scala.util.Try

/**
 * Created by Alexander on 25.06.2016.
 */
trait QueryExecutor {
  def executeQuery(connectionPoolName: String)(query: String): Unit

  def executeReadOnlyQuery[T, R](connectionPoolName: String)(query: String)(implicit dataExtractor: ResultSet => (T, R)): Try[ListMap[T, R]]
}
