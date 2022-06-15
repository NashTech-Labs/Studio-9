package baile.dao.mongo

import org.mongodb.scala.Document
import org.mongodb.scala.bson.{ BsonArray, BsonDocument, BsonValue }

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

object BsonHelpers {
  implicit class DocumentExtensions(document: Document) {
    def getMandatory[B <: BsonValue](key: String)(implicit ct: ClassTag[B]): B = {
      if (document.contains(key)) {
        document.get[B](key) match {
          case Some(value) => value
          case None => throw MongoInvalidValueException(document, key, ct.toString)
        }
      } else throw MongoMissingKeyException(document, key)
    }
    def getChild(key: String): Option[Document] = document.get[BsonDocument](key).map(Document(_))
    def getChildMandatory(key: String): Document = Document(document.getMandatory[BsonDocument](key))
  }

  implicit class BsonArrayExtensions(array: BsonArray) {
    def asScala: Seq[BsonValue] = array.getValues.asScala
    def map[B](f: BsonValue => B): Seq[B] = {
      asScala.map(f)
    }
  }
}
