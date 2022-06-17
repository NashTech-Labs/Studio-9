package baile.dao.tabular.prediction

import java.time.Instant
import java.util.UUID

import baile.dao.asset.Filters._
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.dao.tabular.prediction.TabularPredictionDao._
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.tabular.prediction
import baile.domain.tabular.prediction.{ ColumnMapping, TabularPrediction, TabularPredictionStatus }
import org.mongodb.scala.bson.{ BsonArray, BsonDocument, BsonString }
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object TabularPredictionDao {

  case class StatusIs(status: TabularPredictionStatus) extends Filter
  case class TableIdIs(albumId: String) extends Filter
  case class TabularModelIdIs(modelId: String) extends Filter

  case object Name extends Field
  case object Created extends Field
  case object Updated extends Field

}

class TabularPredictionDao(protected val database: MongoDatabase) extends MongoEntityDao[TabularPrediction] {

  override val collectionName: String = "tabularPredictions"

  override protected val fieldMapper: Map[Field, String] =
    Map(
      Name -> "name",
      Created -> "created",
      Updated -> "updated"
    )
  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case OwnerIdIs(ownerId) => Try(MongoFilters.equal("ownerId", ownerId.toString))
    case NameIs(name) => Try(MongoFilters.equal("name", name))
    case InLibraryIs(inLibrary) => Try(MongoFilters.exists("_id", inLibrary))
    case StatusIs(status) => Try(MongoFilters.equal("status", tabularPredictionStatusToString(status)))
    // TODO add protection from injections to regex
    case SearchQuery(term) => Try(MongoFilters.regex("name", term, "i"))
    case TableIdIs(tableId) => Try(MongoFilters.or(
      MongoFilters.equal("inputTableId", tableId),
      MongoFilters.equal("outputTableId", tableId)
    ))
    case TabularModelIdIs(modelId) => Try(MongoFilters.equal("modelId", modelId))
  }

  override protected[prediction] def entityToDocument(entity: TabularPrediction): Document = Document(
    "ownerId" -> BsonString(entity.ownerId.toString),
    "modelId" -> BsonString(entity.modelId),
    "name" -> BsonString(entity.name),
    "inputTableId" -> BsonString(entity.inputTableId),
    "outputTableId" -> BsonString(entity.outputTableId),
    "status" -> BsonString(tabularPredictionStatusToString(entity.status)),
    "created" -> BsonString(entity.created.toString),
    "updated" -> BsonString(entity.updated.toString),
    "columnMappings" -> BsonArray(entity.columnMappings.map(columnMappingToDocument)),
    "description" -> entity.description.map(BsonString(_))
  )

  override protected[prediction] def documentToEntity(document: Document): Try[TabularPrediction] = Try {
    prediction.TabularPrediction(
      ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
      modelId = document.getMandatory[BsonString]("modelId").getValue,
      name = document.getMandatory[BsonString]("name").getValue,
      inputTableId = document.getMandatory[BsonString]("inputTableId").getValue,
      outputTableId = document.getMandatory[BsonString]("outputTableId").getValue,
      status = stringToTabularPredictionStatus(document.getMandatory[BsonString]("status").getValue),
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
      updated = Instant.parse(document.getMandatory[BsonString]("updated").getValue),
      columnMappings = document.getMandatory[BsonArray]("columnMappings").asScala
        .map(value => documentToColumnMapping(value.asDocument)),
      description = document.get[BsonString]("description").map(_.getValue)
    )
  }

  private def documentToColumnMapping(document: Document) = ColumnMapping(
    trainName = document.getMandatory[BsonString]("trainName").getValue,
    currentName = document.getMandatory[BsonString]("currentName").getValue
  )

  private def columnMappingToDocument(entity: ColumnMapping): BsonDocument =
    BsonDocument(
      "trainName" -> BsonString(entity.trainName),
      "currentName" -> BsonString(entity.currentName)
    )

  private def stringToTabularPredictionStatus(predictionStatus: String): TabularPredictionStatus = {
    predictionStatus match {
      case "NEW" => TabularPredictionStatus.New
      case "RUNNING" => TabularPredictionStatus.Running
      case "ERROR" => TabularPredictionStatus.Error
      case "DONE" => TabularPredictionStatus.Done
    }
  }

  private def tabularPredictionStatusToString(predictionStatus: TabularPredictionStatus): String = {
    predictionStatus match {
      case TabularPredictionStatus.New => "NEW"
      case TabularPredictionStatus.Running => "RUNNING"
      case TabularPredictionStatus.Error => "ERROR"
      case TabularPredictionStatus.Done => "DONE"
    }
  }

}
