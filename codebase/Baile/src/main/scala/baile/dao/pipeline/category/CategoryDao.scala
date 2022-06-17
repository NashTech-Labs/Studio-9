package baile.dao.pipeline.category

import baile.dao.asset.Filters.SearchQuery
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.dao.pipeline.category.CategoryDao._
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.pipeline.category.Category
import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object CategoryDao {

  case object Name extends Field
  case object Id extends Field

  case class CategoryIdIs(id: String) extends Filter
  case class NameIs(name: String) extends Filter

}

class CategoryDao(protected val database: MongoDatabase) extends MongoEntityDao[Category] {

  override val collectionName: String = "categories"

  override protected val fieldMapper: Map[Field, String] =
    Map(
      Id -> "id",
      Name -> "name"
    )

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case SearchQuery(term) => Try(MongoFilters.regex("name", term, "i"))
    case CategoryIdIs(id) => Try(MongoFilters.equal("id", id))
    case NameIs(name) => Try(MongoFilters.equal("name", name))
  }

  override protected[category] def entityToDocument(entity: Category): Document = Document(
    "id" -> BsonString(entity.id),
    "name" -> BsonString(entity.name),
    "icon" -> BsonString(entity.icon)
  )

  override protected[category] def documentToEntity(document: Document): Try[Category] = Try {
    Category(
      id = document.getMandatory[BsonString]("id").getValue,
      name = document.getMandatory[BsonString]("name").getValue,
      icon = document.getMandatory[BsonString]("icon").getValue
    )
  }

}
