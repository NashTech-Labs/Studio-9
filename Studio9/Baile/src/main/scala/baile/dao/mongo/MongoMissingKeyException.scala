package baile.dao.mongo

import org.mongodb.scala.Document

case class MongoMissingKeyException(
  document: Document,
  key: String
) extends RuntimeException(
  s"No '$key' value found in Mongo document. Document: ${ document.toJson }"
)
