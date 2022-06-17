package baile.dao.cv.model.tlprimitives

import baile.dao.OperatorParameterSerializers._
import baile.dao.cv.model.tlprimitives.CVTLModelPrimitiveDao._
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.cv.model.tlprimitives.{ CVTLModelPrimitive, CVTLModelPrimitiveType }
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonString }
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object CVTLModelPrimitiveDao {

  case class PackageIdIs(packageId: String) extends Filter

  case class CVTLModelPrimitiveTypeIs(cvTLModelPrimitiveType: CVTLModelPrimitiveType) extends Filter

  case class ModuleNameIs(moduleName: String) extends Filter

  case class ClassNameIs(className: String) extends Filter

  case class PackageIdIn(packageIds: Seq[String]) extends Filter

}

class CVTLModelPrimitiveDao(
  protected val database: MongoDatabase
) extends MongoEntityDao[CVTLModelPrimitive] {

  override val collectionName: String = "CVTLModelPrimitives"

  override protected val fieldMapper: Map[Field, String] = Map.empty[Field, String]
  override protected val specificFilterMapper: PartialFunction[Filter, Try[Bson]] = {
    case PackageIdIs(packageId) => Try(MongoFilters.eq("packageId", packageId))
    case CVTLModelPrimitiveTypeIs(cvTLModelPrimitiveType) =>
      Try(MongoFilters.eq("cvTLModelPrimitiveType", cvTLModelPrimitiveTypeToString(cvTLModelPrimitiveType)))
    case ModuleNameIs(moduleName) => Try(MongoFilters.eq("moduleName", moduleName))
    case ClassNameIs(className) => Try(MongoFilters.eq("className", className))
    case PackageIdIn(packageIds) => Try(MongoFilters.in("packageId", packageIds: _*))
  }

  override protected[tlprimitives] def entityToDocument(modelPrimitive: CVTLModelPrimitive): Document = Document(
    "packageId" -> BsonString(modelPrimitive.packageId),
    "name" -> BsonString(modelPrimitive.name),
    "description" -> modelPrimitive.description.map(BsonString(_)),
    "moduleName" -> BsonString(modelPrimitive.moduleName),
    "className" -> BsonString(modelPrimitive.className),
    "cvTLModelPrimitiveType" -> BsonString(cvTLModelPrimitiveTypeToString(modelPrimitive.cvTLModelPrimitiveType)),
    "params" -> BsonArray(modelPrimitive.params.map(paramsToDocument)),
    "isNeural" -> BsonBoolean(modelPrimitive.isNeural)
  )

  override protected[tlprimitives] def documentToEntity(document: Document): Try[CVTLModelPrimitive] = Try {
    CVTLModelPrimitive(
      packageId = document.getMandatory[BsonString]("packageId").getValue,
      name = document.getMandatory[BsonString]("name").getValue,
      description = document.get[BsonString]("description").map(_.getValue),
      moduleName = document.getMandatory[BsonString]("moduleName").getValue,
      className = document.getMandatory[BsonString]("className").getValue,
      cvTLModelPrimitiveType = stringToCVTLModelPrimitive(
        document.getMandatory[BsonString]("cvTLModelPrimitiveType").getValue
      ),
      params = document.getMandatory[BsonArray]("params") map { value =>
        documentToOperatorParameter(value.asDocument())
      },
      isNeural = document.getMandatory[BsonBoolean]("isNeural").getValue
    )
  }

  private def cvTLModelPrimitiveTypeToString(cvTLModelPrimitiveType: CVTLModelPrimitiveType): String = {
    cvTLModelPrimitiveType match {
      case CVTLModelPrimitiveType.Detector => "DETECTOR"
      case CVTLModelPrimitiveType.Classifier => "CLASSIFIER"
      case CVTLModelPrimitiveType.UTLP => "UTLP"
      case CVTLModelPrimitiveType.Decoder => "DECODER"
    }
  }

  private def stringToCVTLModelPrimitive(cvTLModelPrimitiveType: String): CVTLModelPrimitiveType = {
    cvTLModelPrimitiveType match {
      case "DETECTOR" => CVTLModelPrimitiveType.Detector
      case "CLASSIFIER" => CVTLModelPrimitiveType.Classifier
      case "UTLP" => CVTLModelPrimitiveType.UTLP
      case "DECODER" => CVTLModelPrimitiveType.Decoder
    }
  }

}
