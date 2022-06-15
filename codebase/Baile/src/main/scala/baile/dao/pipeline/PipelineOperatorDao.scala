package baile.dao.pipeline

import baile.dao.OperatorParameterSerializers._
import baile.dao.asset.Filters.SearchQuery
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.pipeline.{ PipelineDataType, _ }
import baile.utils.CollectionExtensions.seqToOptionalNonEmptySeq
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonDocument, BsonString }
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object PipelineOperatorDao {

  case class ModuleNameIs(moduleName: String) extends Filter
  case class ClassNameIs(className: String) extends Filter
  case class PackageIdIs(packageId: String) extends Filter
  case class PackageIdIn(packageIds: Seq[String]) extends Filter

}

class PipelineOperatorDao(protected val database: MongoDatabase) extends MongoEntityDao[PipelineOperator] {

  import baile.dao.pipeline.PipelineOperatorDao._

  override val collectionName: String = "pipelineOperators"

  override protected val fieldMapper: Map[Field, String] = Map.empty[Field, String]

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case ModuleNameIs(moduleName) => Try(MongoFilters.equal("moduleName", moduleName))
    case ClassNameIs(className) => Try(MongoFilters.equal("className", className))
    case PackageIdIs(packageId) => Try(MongoFilters.eq("packageId", packageId))
    case PackageIdIn(packageIds) => Try(MongoFilters.in("packageId", packageIds: _*))
    case SearchQuery(term) => Try(MongoFilters.regex("name", term, "i"))
  }

  override protected[pipeline] def entityToDocument(entity: PipelineOperator): Document = Document(
    "name" -> BsonString(entity.name),
    "description" -> entity.description.map(BsonString(_)),
    "category" ->  entity.category.map(BsonString(_)),
    "className" -> BsonString(entity.className),
    "moduleName" -> BsonString(entity.moduleName),
    "packageId" -> BsonString(entity.packageId),
    "inputs" -> entity.inputs.map(inputToDocument),
    "outputs" -> entity.outputs.map(outputToDocument),
    "params" -> entity.params.map(paramsToDocument)
  )

  override protected[pipeline] def documentToEntity(document: Document): Try[PipelineOperator] = Try {
    PipelineOperator(
      name = document.getMandatory[BsonString]("name").getValue,
      description = document.get[BsonString]("description").map(_.getValue),
      category = document.get[BsonString]("category").map(_.getValue),
      className = document.getMandatory[BsonString]("className").getValue,
      moduleName = document.getMandatory[BsonString]("moduleName").getValue,
      packageId = document.getMandatory[BsonString]("packageId").getValue,
      inputs = document.getMandatory[BsonArray]("inputs") map { value =>
        documentToInput(value.asDocument())
      },
      outputs = document.getMandatory[BsonArray]("outputs") map { value =>
        documentToOutput(value.asDocument())
      },
      params = document.getMandatory[BsonArray]("params") map { value =>
        documentToOperatorParameter(value.asDocument())
      }
    )
  }

  def documentToComplexDataType(document: Document): ComplexDataType = {
    ComplexDataType(
      definition = document.getMandatory[BsonString]("definition").getValue,
      parents = document.get[BsonArray]("parents").map(_.map(
        elem => documentToComplexDataType(elem.asDocument)
      )).getOrElse(Seq.empty),
      typeArguments = document.get[BsonArray]("typeArguments").map(_.map(
        elem => documentToPipelineDataType(elem.asDocument)
      )).getOrElse(Seq.empty)
    )
  }

  private def documentToPipelineDataType(document: Document): PipelineDataType = {
    document.getMandatory[BsonString]("type").getValue match {
      case "PRIMITIVE" => document.getMandatory[BsonString]("value").getValue match {
        case "STRING" => PrimitiveDataType.String
        case "FLOAT" => PrimitiveDataType.Float
        case "INTEGER" => PrimitiveDataType.Integer
        case "BOOLEAN" => PrimitiveDataType.Boolean
      }
      case "COMPLEX" => documentToComplexDataType(document.getMandatory[BsonDocument]("value"))
    }
  }

  private def documentToInput(document: Document) = PipelineOperatorInput(
    name = document.getMandatory[BsonString]("name").getValue,
    description = document.get[BsonString]("description").map(_.getValue),
    `type` = documentToPipelineDataType(document.getChildMandatory("type")),
    covariate = document.getMandatory[BsonBoolean]("covariate").getValue,
    required = document.getMandatory[BsonBoolean]("required").getValue
  )

  private def documentToOutput(document: Document) = PipelineOperatorOutput(
    description = document.get[BsonString]("description").map(_.getValue),
    `type` = documentToPipelineDataType(document.getChildMandatory("type"))
  )

  private def outputToDocument(output: PipelineOperatorOutput) =
    Document(
      "description" -> output.description.map(BsonString(_)),
      "type" -> dataTypeToDocument(output.`type`)
    )

  private def inputToDocument(input: PipelineOperatorInput) =
    Document(
      "name" -> BsonString(input.name),
      "description" -> input.description.map(BsonString(_)),
      "type" -> dataTypeToDocument(input.`type`),
      "covariate" -> BsonBoolean(input.covariate),
      "required" -> BsonBoolean(input.required)
    )

  private def convertPrimitiveDataTypeToString(primitiveDataType: PrimitiveDataType): String = primitiveDataType match {
    case PrimitiveDataType.Boolean => "BOOLEAN"
    case PrimitiveDataType.String => "STRING"
    case PrimitiveDataType.Integer => "INTEGER"
    case PrimitiveDataType.Float => "FLOAT"
  }

  private def complexDataTypeToDocument(complexType: ComplexDataType): Document = Document(
    "definition" -> BsonString(complexType.definition),
    "parents" -> seqToOptionalNonEmptySeq(complexType.parents.map(complexDataTypeToDocument)),
    "typeArguments" -> seqToOptionalNonEmptySeq(complexType.typeArguments.map(dataTypeToDocument))
  )

  private def dataTypeToDocument(dataType: PipelineDataType): Document =
    dataType match {
      case primitive: PrimitiveDataType => Document(
        "type" -> BsonString("PRIMITIVE"),
        "value" -> BsonString(convertPrimitiveDataTypeToString(primitive))
      )
      case complex: ComplexDataType => Document(
        "type" -> BsonString("COMPLEX"),
        "value" -> complexDataTypeToDocument(complex)
      )
    }

}
