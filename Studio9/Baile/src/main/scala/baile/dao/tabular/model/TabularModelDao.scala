package baile.dao.tabular.model

import java.time.Instant
import java.util.UUID

import baile.dao.CommonSerializers
import baile.dao.asset.Filters.{ InLibraryIs, NameIs, OwnerIdIs, SearchQuery }
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.dao.tabular.model.TabularModelDao.{ Created, Name, Updated }
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.common.{ ClassReference, CortexModelReference }
import baile.domain.tabular.model.{ ModelColumn, TabularModel, TabularModelStatus }
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonDocument, BsonString }
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

// scalastyle:off number.of.methods
class TabularModelDao(val database: MongoDatabase) extends MongoEntityDao[TabularModel] {

  override val collectionName: String = "tabularModels"

  override protected[model] def entityToDocument(model: TabularModel): Document = Document(
    "ownerId" -> BsonString(model.ownerId.toString),
    "name" -> BsonString(model.name),
    "predictorColumns" -> model.predictorColumns.map(columnToDocument),
    "responseColumn" -> columnToDocument(model.responseColumn),
    "classNames" -> model.classNames.map(_.map(BsonString(_))),
    "classReference" -> classReferenceToDocument(model.classReference),
    "cortexModelReference" -> model.cortexModelReference.map(cortexModelReferenceToDocument),
    "inLibrary" -> BsonBoolean(model.inLibrary),
    "status" -> BsonString(statusToString(model.status)),
    "created" -> BsonString(model.created.toString),
    "updated" -> BsonString(model.updated.toString),
    "description" -> model.description.map(BsonString(_)),
    "experimentId" -> model.experimentId.map(BsonString(_))
  )

  override protected[model] def documentToEntity(document: Document): Try[TabularModel] = Try(TabularModel(
    ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
    name = document.getMandatory[BsonString]("name").getValue,
    predictorColumns = document.getMandatory[BsonArray]("predictorColumns").map(elem =>
      documentToColumn(elem.asDocument)
    ),
    responseColumn = documentToColumn(document.getChildMandatory("responseColumn")),
    classNames = document.get[BsonArray]("classNames").map(_.map(_.asString.getValue)),
    classReference = documentToClassReference(document.getChildMandatory("classReference")),
    cortexModelReference = document.getChild("cortexModelReference").map(documentToCortexModelReference),
    inLibrary = document.getMandatory[BsonBoolean]("inLibrary").getValue,
    status = stringToStatus(document.getMandatory[BsonString]("status").getValue),
    created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
    updated = Instant.parse(document.getMandatory[BsonString]("updated").getValue),
    description = document.get[BsonString]("description").map(_.getValue),
    experimentId = document.get[BsonString]("experimentId").map(_.getValue)
  ))

  override protected val fieldMapper: Map[Field, String] = Map(
    Name -> "name",
    Created -> "created",
    Updated -> "updated"
  )

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Bson]] = {
    case OwnerIdIs(ownerId) => Try(MongoFilters.equal("ownerId", ownerId.toString))
    // TODO add protection from injections to regex
    case SearchQuery(term) => Try(MongoFilters.regex("name", term, "i"))
    case InLibraryIs(inLibrary) => Try(MongoFilters.equal("inLibrary", inLibrary))
    case NameIs(name) => Try(MongoFilters.equal("name", name))
  }

  private def classReferenceToDocument(classReference: ClassReference) = Document(
    "moduleName" -> BsonString(classReference.moduleName),
    "className" -> BsonString(classReference.className),
    "packageId" -> BsonString(classReference.packageId)
  )

  private def columnToDocument(column: ModelColumn): Document = Document(
    "name" -> BsonString(column.name),
    "displayName" -> BsonString(column.displayName),
    "dataType" -> BsonString(CommonSerializers.columnDataTypeToString(column.dataType)),
    "variableType" -> BsonString(CommonSerializers.columnVariableTypeToString(column.variableType))
  )

  private def cortexModelReferenceToDocument(cortexModelReference: CortexModelReference): Document = BsonDocument(
    "cortexId" -> BsonString(cortexModelReference.cortexId),
    "cortexFilePath" -> BsonString(cortexModelReference.cortexFilePath)
  )

  private def statusToString(status: TabularModelStatus): String = status match {
    case TabularModelStatus.Active => "ACTIVE"
    case TabularModelStatus.Training => "TRAINING"
    case TabularModelStatus.Predicting => "PREDICTING"
    case TabularModelStatus.Error => "ERROR"
    case TabularModelStatus.Cancelled => "CANCELLED"
    case TabularModelStatus.Saving => "SAVING"
  }

  private def documentToClassReference(document: Document) = ClassReference(
    moduleName = document.getMandatory[BsonString]("moduleName").getValue,
    className = document.getMandatory[BsonString]("className").getValue,
    packageId = document.getMandatory[BsonString]("packageId").getValue
  )

  private def documentToColumn(document: Document): ModelColumn = ModelColumn(
    name = document.getMandatory[BsonString]("name").getValue,
    displayName = document.getMandatory[BsonString]("displayName").getValue,
    dataType = CommonSerializers.columnDataTypeFromString(document.getMandatory[BsonString]("dataType").getValue),
    variableType = CommonSerializers.columnVariableTypeFromString(
      document.getMandatory[BsonString]("variableType").getValue
    )
  )

  private def documentToCortexModelReference(document: Document): CortexModelReference = CortexModelReference(
    cortexId = document.getMandatory[BsonString]("cortexId").getValue,
    cortexFilePath = document.getMandatory[BsonString]("cortexFilePath").getValue
  )

  private def stringToStatus(string: String): TabularModelStatus = string match {
    case "ACTIVE" => TabularModelStatus.Active
    case "TRAINING" => TabularModelStatus.Training
    case "PREDICTING" => TabularModelStatus.Predicting
    case "ERROR" => TabularModelStatus.Error
    case "CANCELLED" => TabularModelStatus.Cancelled
    case "SAVING" => TabularModelStatus.Saving
  }

}
// scalastyle:on number.of.methods

object TabularModelDao {
  case object Name extends Field
  case object Created extends Field
  case object Updated extends Field

  case class TableIdIs(tableId: String) extends Filter
}
