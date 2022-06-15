package baile.dao.dcproject

import java.time.Instant
import java.util.UUID

import baile.dao.asset.Filters.SearchQuery
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.dcproject.DCProjectPackage
import baile.domain.common.{ Version => DomainVersion }
import org.mongodb.scala.bson.{ BsonBoolean, BsonNull, BsonString }
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object DCProjectPackageDao {

  case object Name extends Field
  case object Created extends Field
  case object Version extends Field

  case class DCProjectIdIs(projectId: Option[String]) extends Filter {
    def this(projectId: String) = this(Some(projectId))
  }
  case class NameIs(name: String) extends Filter
  case class OwnerIdIs(ownerId: UUID) extends Filter
  case class VersionIs(version: DomainVersion) extends Filter
  case object HasNoVersion extends Filter
  case object HasLocation extends Filter
  case object IsPublished extends Filter

  object DCProjectIdIs {
    def apply(projectId: String): DCProjectIdIs = new DCProjectIdIs(projectId)
  }
}

class DCProjectPackageDao(protected val database: MongoDatabase) extends MongoEntityDao[DCProjectPackage] {

  import baile.dao.dcproject.DCProjectPackageDao._

  override val collectionName: String = "DCProjectPackages"

  override protected val fieldMapper: Map[Field, String] =
    Map(
      Name -> "name",
      Created -> "created",
      Version -> "version"
    )

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case DCProjectIdIs(Some(projectId)) => Try(MongoFilters.eq("dcProjectId", projectId))
    case DCProjectIdIs(None) => Try(MongoFilters.or(
      MongoFilters.exists("dcProjectId", exists = false),
      MongoFilters.eq("dcProjectId", BsonNull())
    ))
    case NameIs(name) => Try(MongoFilters.eq("name", name))
    case SearchQuery(term) => Try(MongoFilters.regex("name", term, "i"))
    case OwnerIdIs(ownerId) => Try(MongoFilters.equal("ownerId", ownerId.toString))
    case VersionIs(version) => Try(MongoFilters.equal("version", version.toString))
    case HasNoVersion => Try(MongoFilters.exists("version", exists = false))
    case HasLocation => Try(MongoFilters.exists("location", exists = true))
    case IsPublished => Try(MongoFilters.eq("isPublished", true))
  }

  override protected[dcproject] def entityToDocument(entity: DCProjectPackage): Document = Document(
    "ownerId" -> entity.ownerId.map(id => BsonString(id.toString)),
    "dcProjectId" -> entity.dcProjectId.map(BsonString(_)),
    "name" -> BsonString(entity.name),
    "version" -> entity.version.map(version => BsonString(version.toString)),
    "location" -> entity.location.map(BsonString(_)),
    "created" -> BsonString(entity.created.toString),
    "description" -> entity.description.map(BsonString(_)),
    "isPublished" -> BsonBoolean(entity.isPublished)
  )

  override protected[dcproject] def documentToEntity(document: Document): Try[DCProjectPackage] = Try {
    DCProjectPackage(
      ownerId = document.get[BsonString]("ownerId").map(id => UUID.fromString(id.getValue)),
      dcProjectId = document.get[BsonString]("dcProjectId").map(_.getValue),
      name = document.getMandatory[BsonString]("name").getValue,
      version = document.get[BsonString]("version").map { bsonString =>
        DomainVersion.parseFrom(bsonString.getValue).get
      },
      location = document.get[BsonString]("location").map(_.getValue),
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
      description = document.get[BsonString]("description").map(_.getValue),
      isPublished = document.getMandatory[BsonBoolean]("isPublished").getValue
    )
  }

  def listPackageNames(filter: Filter)(implicit ec: ExecutionContext): Future[Seq[String]] =
    listDistinctValues(fieldMapper(Name), filter, (_: BsonString).getValue)

}
