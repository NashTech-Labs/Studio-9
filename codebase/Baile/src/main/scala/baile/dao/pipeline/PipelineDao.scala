package baile.dao.pipeline

import java.time.Instant
import java.util.UUID

import baile.dao.asset.Filters.{ InLibraryIs, NameIs, OwnerIdIs, SearchQuery }
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.pipeline._
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonString }
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object PipelineDao {

  case object Name extends Field

  case object Created extends Field

  case object Updated extends Field

  case class OperatorIdIs(operatorId: String) extends Filter

}

class PipelineDao(protected val database: MongoDatabase) extends MongoEntityDao[Pipeline] {

  import baile.dao.pipeline.PipelineDao._

  override val collectionName: String = "pipelines"

  override protected val fieldMapper: Map[Field, String] =
    Map(
      Name -> "name",
      Created -> "created",
      Updated -> "updated"
    )

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case OwnerIdIs(ownerId) => Try(MongoFilters.equal("ownerId", ownerId.toString))
    case SearchQuery(term) => Try(MongoFilters.regex("name", term, "i"))
    case InLibraryIs(inLibrary) => Try(MongoFilters.equal("inLibrary", inLibrary))
    case OperatorIdIs(operatorId) => Try(MongoFilters.equal("steps.operatorId", operatorId))
    case NameIs(name) => Try(MongoFilters.equal("name", name))
  }

  override protected[pipeline] def entityToDocument(entity: Pipeline): Document = Document(
    "name" -> BsonString(entity.name),
    "ownerId" -> BsonString(entity.ownerId.toString),
    "status" -> statusToValue(entity.status),
    "created" -> BsonString(entity.created.toString),
    "updated" -> BsonString(entity.updated.toString),
    "inLibrary" -> BsonBoolean(entity.inLibrary),
    "description" -> entity.description.map(BsonString(_)),
    "steps" -> entity.steps.map(pipelineStepInfoToDocument)
  )

  override protected[pipeline] def documentToEntity(document: Document): Try[Pipeline] = Try {
    Pipeline(
      name = document.getMandatory[BsonString]("name").getValue,
      ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
      status = valueToStatus(document.getMandatory[BsonString]("status")),
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
      updated = Instant.parse(document.getMandatory[BsonString]("updated").getValue),
      inLibrary = document.getMandatory[BsonBoolean]("inLibrary").getValue,
      description = document.get[BsonString]("description").map(_.getValue),
      steps = document.getMandatory[BsonArray]("steps") map { value =>
        documentToPipelineStepInfo(value.asDocument())
      }
    )
  }

  private def valueToStatus(document: BsonString): PipelineStatus = {
    document.getValue match {
      case "IDLE" => PipelineStatus.Idle
    }
  }

  private def statusToValue(status: PipelineStatus): BsonString = {
    status match {
      case PipelineStatus.Idle => BsonString("IDLE")
    }
  }

  private def pipelineStepInfoToDocument(entity: PipelineStepInfo): Document =
    PipelineStepSerializer.pipelineStepToDocument(entity.step) + (
      "pipelineParameters" -> Document(entity.pipelineParameters)
    )

  private def documentToPipelineStepInfo(document: Document): PipelineStepInfo =
    PipelineStepInfo(
      step = PipelineStepSerializer.documentToPipelineStep(document),
      pipelineParameters = document
        .getChildMandatory("pipelineParameters")
        .toMap
        .mapValues(_.asString().getValue)
    )

}
