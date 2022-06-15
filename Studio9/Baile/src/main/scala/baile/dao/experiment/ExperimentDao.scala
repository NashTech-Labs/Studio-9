package baile.dao.experiment

import java.time.Instant
import java.util.UUID

import baile.dao.asset.Filters.{ InLibraryIs, NameIs, OwnerIdIs, SearchQuery }
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.experiment.{ Experiment, ExperimentStatus }
import org.mongodb.scala.bson._
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

class ExperimentDao(
  protected val database: MongoDatabase,
  val serializerDelegator: SerializerDelegator
) extends MongoEntityDao[Experiment] {

  override val collectionName: String = "experiments"

  override protected val fieldMapper: Map[Field, String] = Map(
    ExperimentDao.Name -> "name",
    ExperimentDao.Created -> "created",
    ExperimentDao.Updated -> "updated"
  )

  val basicFilter: PartialFunction[Filter, Try[Bson]] = {
    case OwnerIdIs(userId) => Try(Filters.equal("ownerId", userId.toString))
    case NameIs(name) => Try(Filters.equal("name", name))
    case InLibraryIs(inLibrary) => Try(Filters.exists("_id", inLibrary))
    case SearchQuery(term) => Try(Filters.regex("name", term, "i"))
  }

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Bson]] =
    basicFilter orElse serializerDelegator.filterMapper

  override protected[experiment] def entityToDocument(entity: Experiment): Document = Document(
    "ownerId" -> BsonString(entity.ownerId.toString),
    "name" -> BsonString(entity.name),
    "created" -> BsonString(entity.created.toString),
    "updated" -> BsonString(entity.updated.toString),
    "status" -> BsonString(entity.status match {
      case ExperimentStatus.Running => "RUNNING"
      case ExperimentStatus.Completed => "COMPLETED"
      case ExperimentStatus.Error => "ERROR"
      case ExperimentStatus.Cancelled => "CANCELLED"
    }),
    "pipeline" -> serializerDelegator.pipelineToDocument(entity.pipeline),
    "result" -> entity.result.map(serializerDelegator.resultToDocument),
    "description" -> entity.description.map(BsonString(_))
  )

  override protected[experiment] def documentToEntity(document: Document): Try[Experiment] = Try {
    Experiment(
      name = document.getMandatory[BsonString]("name").getValue,
      ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
      description = document.get[BsonString]("description").map(_.getValue),
      status = document.getMandatory[BsonString]("status").getValue match {
        case "RUNNING" => ExperimentStatus.Running
        case "COMPLETED" => ExperimentStatus.Completed
        case "ERROR" => ExperimentStatus.Error
        case "CANCELLED" => ExperimentStatus.Cancelled
      },
      pipeline = serializerDelegator.documentToPipeline(document.getChildMandatory("pipeline")),
      result = document.getChild("result").map(serializerDelegator.documentToResult),
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
      updated = Instant.parse(document.getMandatory[BsonString]("updated").getValue)
    )
  }

}

object ExperimentDao {

  case object Name extends Field
  case object Created extends Field
  case object Updated extends Field

}
