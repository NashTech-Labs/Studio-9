package baile.dao.dcproject

import java.time.Instant
import java.util.UUID

import baile.dao.asset.Filters.{ InLibraryIs, NameIs, OwnerIdIs, SearchQuery }
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.common.Version
import baile.domain.dcproject.{ DCProject, DCProjectStatus }
import org.bson.BsonType
import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object DCProjectDao {

  case object Name extends Field
  case object Created extends Field
  case object Updated extends Field

  case class PackageNameIs(packageName: String) extends Filter

}

class DCProjectDao(protected val database: MongoDatabase) extends MongoEntityDao[DCProject] {

  import baile.dao.dcproject.DCProjectDao._

  override val collectionName: String = "DCProjects"

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
    case PackageNameIs(packageName) => Try(
      MongoFilters.and(
        // This is needed because index on this field is created with this partial filter.
        // See https://stackoverflow.com/questions/35755628/u#comment95827293_39848209
        MongoFilters.`type`("packageName", BsonType.STRING),
        MongoFilters.equal("packageName", packageName)
      )
    )
  }

  override protected[dcproject] def entityToDocument(entity: DCProject): Document = Document(
    "ownerId" -> entity.ownerId.toString,
    "name" -> entity.name,
    "status" -> BsonString(entity.status match {
      case DCProjectStatus.Idle => "IDLE"
      case DCProjectStatus.Interactive => "INTERACTIVE"
      case DCProjectStatus.Building => "BUILDING"
    }),
    "created" -> entity.created.toString,
    "updated" -> entity.updated.toString,
    "description" -> entity.description.map(BsonString(_)),
    "basePath" -> entity.basePath,
    "packageName" -> entity.packageName.map(BsonString(_)),
    "latestPackageVersion" -> entity.latestPackageVersion.map(version => BsonString(version.toString))
  )

  override protected[dcproject] def documentToEntity(document: Document): Try[DCProject] = Try {
    DCProject(
      ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
      name = document.getMandatory[BsonString]("name").getValue,
      status = document.getMandatory[BsonString]("status").getValue match {
        case "IDLE" => DCProjectStatus.Idle
        case "INTERACTIVE" => DCProjectStatus.Interactive
        case "BUILDING" => DCProjectStatus.Building
      },
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
      updated = Instant.parse(document.getMandatory[BsonString]("updated").getValue),
      description = document.get[BsonString]("description").map(_.getValue),
      basePath = document.getMandatory[BsonString]("basePath").getValue,
      packageName = document.get[BsonString]("packageName").map(_.getValue),
      latestPackageVersion = document.get[BsonString]("latestPackageVersion").map { bsonString =>
        Version.parseFrom(bsonString.getValue).get
      }
    )
  }

}
