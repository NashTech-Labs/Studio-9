package baile.dao.table

import java.time.Instant
import java.util.UUID

import baile.dao.CommonSerializers
import baile.dao.asset.Filters.{ InLibraryIs, NameIs, OwnerIdIs, SearchQuery }
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.dao.table.TableDao.{ RepositoryIdIs, _ }
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.table._
import org.mongodb.scala.bson._
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object TableDao {

  case object Name extends Field
  case object Created extends Field
  case object Updated extends Field
  case class RepositoryIdIs(repoId: String) extends Filter

}

class TableDao(protected val database: MongoDatabase) extends MongoEntityDao[Table] {

  override val collectionName: String = "tables"

  override protected val fieldMapper: Map[Field, String] =
    Map(
      Name -> "name",
      Created -> "createdDate",
      Updated -> "modifiedDate"
    )
  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case OwnerIdIs(ownerId) => Try(MongoFilters.equal("ownerId", ownerId.toString))
    // TODO add mapping for particular column name and value for it
    case SearchQuery(term) => Try(MongoFilters.regex("name", term, "i"))
    case InLibraryIs(inLibrary) => Try(MongoFilters.equal("inLibrary", inLibrary))
    case NameIs(name) => Try(MongoFilters.equal("name", name))
    case RepositoryIdIs(repositoryId) => Try(MongoFilters.equal("repositoryId", repositoryId))
  }

  override protected[table] def entityToDocument(entity: Table): Document = Document(
    "ownerId" -> BsonString(entity.ownerId.toString),
    "name" -> BsonString(entity.name),
    "repositoryId" -> BsonString(entity.repositoryId),
    "databaseId" -> BsonString(entity.databaseId),
    "createdDate" -> BsonString(entity.created.toString),
    "modifiedDate" -> BsonString(entity.updated.toString),
    "status" -> BsonString(entity.status match {
      case TableStatus.Active => "ACTIVE"
      case TableStatus.Inactive => "INACTIVE"
      case TableStatus.Saving => "SAVING"
      case TableStatus.Error => "ERROR"
    }),
    "columns" -> BsonArray(entity.columns.map(columnToDocument)),
    "datasetType" -> BsonString(entity.`type` match {
      case TableType.Source => "SOURCE"
      case TableType.Derived => "DERIVED"
    }),
    "size" -> entity.size.map(value => BsonInt64(value)),
    "inLibrary" -> BsonBoolean(entity.inLibrary),
    "tableStatisticsStatus" -> BsonString(entity.tableStatisticsStatus match {
      case TableStatisticsStatus.Done => "DONE"
      case TableStatisticsStatus.Pending => "PENDING"
      case TableStatisticsStatus.Error => "ERROR"
    }),
    "description" -> entity.description.map(BsonString(_))
  )

  override protected[table] def documentToEntity(document: Document): Try[Table] = Try {
    Table(
      ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
      name = document.getMandatory[BsonString]("name").getValue,
      repositoryId = document.getMandatory[BsonString]("repositoryId").getValue,
      databaseId = document.getMandatory[BsonString]("databaseId").getValue,
      created = Instant.parse(document.getMandatory[BsonString]("createdDate").getValue),
      updated = Instant.parse(document.getMandatory[BsonString]("modifiedDate").getValue),
      status = document.getMandatory[BsonString]("status").getValue match {
        case "ACTIVE" => TableStatus.Active
        case "INACTIVE" => TableStatus.Inactive
        case "SAVING" => TableStatus.Saving
        case "ERROR" => TableStatus.Error
      },
      columns = document.getMandatory[BsonArray]("columns").asScala
        .map(value => documentToColumn(value.asDocument)),
      `type` = document.getMandatory[BsonString]("datasetType").getValue match {
        case "SOURCE" => TableType.Source
        case "DERIVED" => TableType.Derived
      },
      size = document.get[BsonInt64]("size").map(_.getValue),
      inLibrary = document.getMandatory[BsonBoolean]("inLibrary").getValue,
      tableStatisticsStatus = document.getMandatory[BsonString]("tableStatisticsStatus").getValue match {
        case "ERROR" => TableStatisticsStatus.Error
        case "PENDING" => TableStatisticsStatus.Pending
        case "DONE" => TableStatisticsStatus.Done
      },
      description = document.get[BsonString]("description").map(_.getValue)
    )
  }

  private def columnToDocument(column: Column) = BsonDocument(
    "name" -> BsonString(column.name),
    "displayName" -> BsonString(column.displayName),
    "dataType" -> BsonString(CommonSerializers.columnDataTypeToString(column.dataType)),
    "variableType" -> BsonString(CommonSerializers.columnVariableTypeToString(column.variableType)),
    "align" -> BsonString(column.align match {
      case ColumnAlign.Center => "CENTER"
      case ColumnAlign.Left => "LEFT"
      case ColumnAlign.Right => "RIGHT"
    }),
    "statistics" -> column.statistics.map(columnStatisticsToDocument)
  )

  private def columnStatisticsToDocument(columnStatistics: ColumnStatistics): BsonDocument = {
    columnStatistics match {
      case value: NumericalStatistics => numericalStatisticsToDocument(value)
      case value: CategoricalStatistics => categoricalStatisticsToDocument(value)
    }
  }

  private def numericalStatisticsToDocument(numericalStatistics: NumericalStatistics): BsonDocument = {
    BsonDocument(
      "min" -> BsonDouble(numericalStatistics.min),
      "max" -> BsonDouble(numericalStatistics.max),
      "avg" -> BsonDouble(numericalStatistics.avg),
      "std" -> BsonDouble(numericalStatistics.std),
      "stdPopulation" -> BsonDouble(numericalStatistics.stdPopulation),
      "mean" -> BsonDouble(numericalStatistics.mean),
      "numericalHistogram" -> BsonArray(numericalStatistics.numericalHistogram.rows
        .map(columnValueRangeToDocument)
      )
    )
  }

  private def categoricalStatisticsToDocument(categoricalStatistics: CategoricalStatistics): BsonDocument = {
    BsonDocument(
      "uniqueValuesCount" -> BsonInt64(categoricalStatistics.uniqueValuesCount),
      "categoricalHistogram" -> BsonArray(categoricalStatistics.categoricalHistogram.rows
        .map(columnValueFrequencyToDocument)
      )
    )
  }

  private def columnValueRangeToDocument(columnValueRange: NumericalHistogramRow): BsonDocument = {
    BsonDocument(
      "min" -> BsonDouble(columnValueRange.min),
      "max" -> BsonDouble(columnValueRange.max),
      "count" -> BsonInt64(columnValueRange.count)
    )
  }

  private def columnValueFrequencyToDocument(columnValueFrequency: CategoricalHistogramRow): BsonDocument = {
    BsonDocument(
      "value" -> columnValueFrequency.value.map(BsonString(_)),
      "count" -> BsonInt64(columnValueFrequency.count)
    )
  }

  private def documentToColumn(document: Document): Column = {
    val variableType = CommonSerializers.columnVariableTypeFromString(
      document.getMandatory[BsonString]("variableType").getValue
    )
    Column(
      name = document.getMandatory[BsonString]("name").getValue,
      displayName = document.getMandatory[BsonString]("displayName").getValue,
      dataType = CommonSerializers.columnDataTypeFromString(
        document.getMandatory[BsonString]("dataType").getValue
      ),
      variableType = variableType,
      align = document.getMandatory[BsonString]("align").getValue match {
        case "RIGHT" => ColumnAlign.Right
        case "LEFT" => ColumnAlign.Left
        case "CENTER" => ColumnAlign.Center
      },
      statistics = variableType match {
        case ColumnVariableType.Continuous => document.getChild("statistics").map(documentToNumericalStatistics)
        case ColumnVariableType.Categorical => document.getChild("statistics").map(documentToCategoricalStatistics)
      }
    )
  }

  private def documentToNumericalStatistics(document: Document): ColumnStatistics = {
    NumericalStatistics(
      min = document.getMandatory[BsonDouble]("min").getValue,
      max = document.getMandatory[BsonDouble]("max").getValue,
      avg = document.getMandatory[BsonDouble]("avg").getValue,
      std = document.getMandatory[BsonDouble]("std").getValue,
      stdPopulation = document.getMandatory[BsonDouble]("stdPopulation").getValue,
      mean = document.getMandatory[BsonDouble]("mean").getValue,
      numericalHistogram = NumericalHistogram(
        document.getMandatory[BsonArray]("numericalHistogram").asScala
          .map(value => documentToNumericalHistogramRow(value.asDocument))
      )
    )
  }

  private def documentToNumericalHistogramRow(document: Document): NumericalHistogramRow = {
    NumericalHistogramRow(
      min = document.getMandatory[BsonDouble]("min").getValue,
      max = document.getMandatory[BsonDouble]("max").getValue,
      count = document.getMandatory[BsonInt64]("count").getValue
    )
  }

  private def documentToCategoricalStatistics(document: Document): ColumnStatistics = {
    CategoricalStatistics(
      uniqueValuesCount = document.getMandatory[BsonInt64]("uniqueValuesCount").getValue,
      categoricalHistogram = CategoricalHistogram(
        document.getMandatory[BsonArray]("categoricalHistogram").asScala
          .map(value => documentToCategoricalHistogramRow(value.asDocument()))
      )
    )
  }

  private def documentToCategoricalHistogramRow(document: Document): CategoricalHistogramRow = {
    CategoricalHistogramRow(
      value = document.get[BsonString]("value").map(_.getValue),
      count = document.getMandatory[BsonInt64]("count").getValue
    )
  }

}
