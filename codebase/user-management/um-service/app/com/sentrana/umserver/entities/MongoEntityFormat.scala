package com.sentrana.umserver.entities

import org.mongodb.scala._
import play.api.libs.json.{ Format, Json }

/**
 * Created by Paul Lysak on 13.04.16.
 */
trait MongoEntityFormat[E] {
  val collectionName: String

  def toDocument(entity: E): Document

  def fromDocument(doc: Document): E
}

class MongoEntityDefaultFormat[E](val collectionName: String)(implicit jsonFormat: Format[E]) extends MongoEntityFormat[E] {
  override def toDocument(entity: E): Document =
    Document(Json.stringify(Json.toJson(entity)))

  override def fromDocument(doc: Document): E =
    Json.parse(doc.toJson()).as[E]
}
