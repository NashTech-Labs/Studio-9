package baile.dao.onlinejob

import java.time.Instant
import java.util.UUID

import baile.dao.asset.Filters.{ InLibraryIs, OwnerIdIs, SearchQuery }
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.onlinejob.{ OnlineJob, OnlineJobOptions, OnlineJobStatus, OnlinePredictionOptions }
import org.bson.BsonBoolean
import org.mongodb.scala.bson.{ BsonDocument, BsonString }
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object OnlineJobDao {

  case class EnabledIs(enabled: Boolean) extends Filter

  case object Enabled extends Field
  case object Name extends Field
  case object Created extends Field
  case object Updated extends Field

}

// TODO migrate old data from MongoDB according to change in id creation
class OnlineJobDao(protected val database: MongoDatabase) extends MongoEntityDao[OnlineJob] {

  import baile.dao.onlinejob.OnlineJobDao._

  override val collectionName: String = "onlineJobs"

  override protected val fieldMapper: Map[Field, String] =
    Map(
      Enabled -> "enabled",
      Name -> "name",
      Created -> "created",
      Updated -> "updated"
    )

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case OwnerIdIs(ownerId) => Try(MongoFilters.equal("ownerId", ownerId.toString))
    case EnabledIs(enabled) => Try(MongoFilters.equal("enabled", enabled))
    // TODO add protection from injections to regex
    case SearchQuery(term) => Try(MongoFilters.regex("name", term, "i"))
    case InLibraryIs(inLibrary) => Try(MongoFilters.exists("_id", inLibrary))
  }

  override protected[onlinejob] def entityToDocument(entity: OnlineJob): Document = Document(
    "ownerId" -> entity.ownerId.toString,
    "name" -> entity.name,
    "status" -> BsonString(entity.status match {
      case OnlineJobStatus.Running => "RUNNING"
      case OnlineJobStatus.Idle => "IDLE"
    }),
    "options" -> jobOptionsToDocument(entity.options),
    "enabled" -> entity.enabled,
    "created" -> entity.created.toString,
    "updated" -> entity.updated.toString,
    "description" -> entity.description.map(BsonString(_))
  )

  override protected[onlinejob] def documentToEntity(document: Document): Try[OnlineJob] =
    Try {
      OnlineJob(
        ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
        name = document.getMandatory[BsonString]("name").getValue,
        status = document.getMandatory[BsonString]("status").getValue match {
          case "RUNNING" => OnlineJobStatus.Running
          case "IDLE" => OnlineJobStatus.Idle
        },
        options = documentToJobOptions(document.getChildMandatory("options")),
        enabled = document.getMandatory[BsonBoolean]("enabled").getValue,
        created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
        updated = Instant.parse(document.getMandatory[BsonString]("updated").getValue),
        description = document.get[BsonString]("description").map(_.getValue)
      )
    }

  private def jobOptionsToDocument(options: OnlineJobOptions): Document = options match {
    case onlinePredictionOptions: OnlinePredictionOptions =>
      BsonDocument(
        "streamId" -> onlinePredictionOptions.streamId,
        "modelId" -> onlinePredictionOptions.modelId,
        "bucketId" -> onlinePredictionOptions.bucketId,
        "inputImagesPath" -> onlinePredictionOptions.inputImagesPath,
        "outputAlbumId" -> onlinePredictionOptions.outputAlbumId
      )
  }

  private def documentToJobOptions(document: Document): OnlineJobOptions =
    OnlinePredictionOptions(
      streamId = document.getMandatory[BsonString]("streamId").getValue,
      modelId = document.getMandatory[BsonString]("modelId").getValue,
      bucketId = document.getMandatory[BsonString]("bucketId").getValue,
      inputImagesPath = document.getMandatory[BsonString]("inputImagesPath").getValue,
      outputAlbumId = document.getMandatory[BsonString]("outputAlbumId").getValue
    )

}
