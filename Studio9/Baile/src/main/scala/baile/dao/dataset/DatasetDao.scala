package baile.dao.dataset

import java.time.Instant
import java.util.UUID

import baile.dao.asset.Filters.{ InLibraryIs, NameIs, OwnerIdIs, SearchQuery }
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.dataset.{ Dataset, DatasetStatus }
import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object DatasetDao {

  case object Name extends Field
  case object Created extends Field
  case object Updated extends Field

}

class DatasetDao(protected val database: MongoDatabase) extends MongoEntityDao[Dataset] {

  import baile.dao.dataset.DatasetDao._

  override val collectionName: String = "datasets"

  override protected val fieldMapper: Map[Field, String] =
    Map(
      Name -> "name",
      Created -> "created",
      Updated -> "updated"
    )

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case OwnerIdIs(ownerId) => Try(MongoFilters.equal("ownerId", ownerId.toString))
    case NameIs(name) => Try(MongoFilters.equal("name", name))
    case SearchQuery(term) => Try(MongoFilters.regex("name", term, "i"))
    case InLibraryIs(inLibrary) => Try(MongoFilters.exists("_id", inLibrary))

  }

  override protected[dataset] def entityToDocument(entity: Dataset): Document = Document(
    "ownerId" -> entity.ownerId.toString,
    "name" -> entity.name,
    "status" -> BsonString(entity.status match {
      case DatasetStatus.Importing => "IMPORTING"
      case DatasetStatus.Exporting => "EXPORTING"
      case DatasetStatus.Active => "ACTIVE"
      case DatasetStatus.Failed => "FAILED"
    }),
    "created" -> entity.created.toString,
    "updated" -> entity.updated.toString,
    "description" -> entity.description.map(BsonString(_)),
    "basePath" -> entity.basePath
  )

  override protected[dataset] def documentToEntity(document: Document): Try[Dataset] = Try {
    Dataset(
      ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
      name = document.getMandatory[BsonString]("name").getValue,
      status = document.getMandatory[BsonString]("status").getValue match {
        case "IMPORTING" => DatasetStatus.Importing
        case "EXPORTING" => DatasetStatus.Exporting
        case "ACTIVE" => DatasetStatus.Active
        case "FAILED" => DatasetStatus.Failed
      },
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
      updated = Instant.parse(document.getMandatory[BsonString]("updated").getValue),
      description = document.get[BsonString]("description").map(_.getValue),
      basePath = document.getMandatory[BsonString]("basePath").getValue
    )
  }

}
