package baile.dao.mongo

import org.mongodb.scala.Document

case class MongoInvalidValueException(
  document: Document,
  key: String,
  expectedClass: String
) extends RuntimeException(
  s"'$key' value in Mongo document is not of expected type ($expectedClass). Document: ${ document.toJson }"
)
