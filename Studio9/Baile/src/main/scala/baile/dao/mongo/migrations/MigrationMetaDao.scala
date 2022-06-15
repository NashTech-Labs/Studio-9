package baile.dao.mongo.migrations

import java.time.Instant

import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.dao.mongo.BsonHelpers._
import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

class MigrationMetaDao(
  val collectionName: String,
  val database: MongoDatabase
) extends MongoEntityDao[MigrationMeta] {

  override protected def entityToDocument(meta: MigrationMeta): Document =
    Document(
      "id" -> BsonString(meta.id),
      "applied" -> BsonString(meta.applied.toString)
    )

  override protected def documentToEntity(document: Document): Try[MigrationMeta] = Try {
    MigrationMeta(
      id = document.getMandatory[BsonString]("id").getValue,
      applied = Instant.parse(document.getMandatory[BsonString]("applied").getValue)
    )
  }

  override protected val fieldMapper: Map[Field, String] = Map.empty

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Bson]] = PartialFunction.empty

}
