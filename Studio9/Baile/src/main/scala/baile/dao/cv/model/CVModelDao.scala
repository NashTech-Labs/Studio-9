package baile.dao.cv.model

import java.time.Instant
import java.util.UUID

import baile.dao.CommonSerializers.{ classReferenceToDocument, documentToClassReference }
import baile.dao.asset.Filters._
import baile.dao.cv.model.CVModelDao.{ CVFeatureExtractorIdIs, ExperimentIdIs, OperatorIdIs }
import baile.dao.images.AlbumLabelModeSerializers.{ albumLabelModeToString, stringToAlbumLabelMode }
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.common.CortexModelReference
import baile.domain.cv.model._
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonDocument, BsonString }
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

class CVModelDao(
  protected val database: MongoDatabase
) extends MongoEntityDao[CVModel] {
  override val collectionName: String = "CVModels"

  override protected val fieldMapper: Map[Field, String] = Map(
    CVModelDao.Name -> "name",
    CVModelDao.Created -> "created",
    CVModelDao.Updated -> "updated"
  )

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Bson]] = {
    case OwnerIdIs(userId) => Try(Filters.equal("ownerId", userId.toString))
    case NameIs(name) => Try(Filters.equal("name", name))
    // TODO add protection from injections to regex
    case SearchQuery(term) => Try(Filters.regex("name", term, "i"))
    case InLibraryIs(inLibrary) => Try(Filters.equal("inLibrary", inLibrary))
    case CVFeatureExtractorIdIs(feId) => Try(Filters.equal("featureExtractorId", feId))
    case ExperimentIdIs(experimentId) => Try(Filters.equal("experimentId", experimentId))
    case OperatorIdIs(operatorId) => Try(Filters.equal("type.operatorId", operatorId))
  }

  override protected[model] def entityToDocument(entity: CVModel): Document = Document(
    "ownerId" -> BsonString(entity.ownerId.toString),
    "name" -> BsonString(entity.name),
    "created" -> BsonString(entity.created.toString),
    "updated" -> BsonString(entity.updated.toString),
    "status" -> BsonString(entity.status match {
      case CVModelStatus.Saving => "SAVING"
      case CVModelStatus.Active => "ACTIVE"
      case CVModelStatus.Training => "TRAINING"
      case CVModelStatus.Pending => "PENDING"
      case CVModelStatus.Predicting => "PREDICTING"
      case CVModelStatus.Error => "ERROR"
      case CVModelStatus.Cancelled => "CANCELLED"
    }),
    "cortexFeatureExtractorReference" -> entity.cortexFeatureExtractorReference.map(cortexModelReferenceToDocument),
    "cortexModelReference" -> entity.cortexModelReference.map(cortexModelReferenceToDocument),
    "type" -> modelTypeToDocument(entity.`type`),
    "classNames" -> entity.classNames.map(_.map(BsonString(_))),
    "featureExtractorId" -> entity.featureExtractorId.map(BsonString(_)),
    "description" -> entity.description.map(BsonString(_)),
    "inLibrary" -> BsonBoolean(entity.inLibrary),
    "experimentId" -> entity.experimentId.map(BsonString(_))
  )

  override protected[model] def documentToEntity(document: Document): Try[CVModel] = Try {
    CVModel(
      ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
      name = document.getMandatory[BsonString]("name").getValue,
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
      updated = Instant.parse(document.getMandatory[BsonString]("updated").getValue),
      status = document.getMandatory[BsonString]("status").getValue match {
        case "SAVING" => CVModelStatus.Saving
        case "ACTIVE" => CVModelStatus.Active
        case "TRAINING" => CVModelStatus.Training
        case "PENDING" => CVModelStatus.Pending
        case "PREDICTING" => CVModelStatus.Predicting
        case "ERROR" => CVModelStatus.Error
        case "CANCELLED" => CVModelStatus.Cancelled
      },
      cortexFeatureExtractorReference = document.getChild("cortexFeatureExtractorReference").map(
        documentToCortexModelReference
      ),
      cortexModelReference = document.getChild("cortexModelReference").map(documentToCortexModelReference),
      `type` = documentToModelType(document.getChildMandatory("type")),
      classNames = document.get[BsonArray]("classNames").map(_.map(_.asString.getValue)),
      featureExtractorId = document.get[BsonString]("featureExtractorId").map(_.getValue),
      description = document.get[BsonString]("description").map(_.getValue),
      inLibrary = document.getMandatory[BsonBoolean]("inLibrary").getValue,
      experimentId = document.get[BsonString]("experimentId").map(_.getValue)
    )
  }

  protected def cortexModelReferenceToDocument(cortexModelReference: CortexModelReference): Document = BsonDocument(
    "cortexId" -> BsonString(cortexModelReference.cortexId),
    "cortexFilePath" -> BsonString(cortexModelReference.cortexFilePath)
  )

  protected def documentToCortexModelReference(document: Document): CortexModelReference = CortexModelReference(
    cortexId = document.getMandatory[BsonString]("cortexId").getValue,
    cortexFilePath = document.getMandatory[BsonString]("cortexFilePath").getValue
  )

  protected def modelTypeToDocument(modelType: CVModelType): Document = {
    modelType match {
      case tl: CVModelType.TL =>
        tlModelToDocument(tl) + ("type" -> "TL")
      case custom: CVModelType.Custom =>
        customModelToDocument(custom) + ("type" -> "CUSTOM")
    }
  }

  protected def documentToModelType(document: Document): CVModelType = {
    document.getMandatory[BsonString]("type").getValue match {
      case "TL" =>
        documentToTlModel(document)
      case "CUSTOM" =>
        documentToCustomModel(document)
    }
  }

  protected def tlModelToDocument(tl: CVModelType.TL): Document = {
    CVModelDao.tlConsumerToDocument(tl.consumer) + (
      "featureExtractorArchitecture" -> tl.featureExtractorArchitecture
    )
  }

  protected def documentToTlModel(document: Document): CVModelType.TL = {
    CVModelType.TL(
      consumer = CVModelDao.documentToTLConsumer(document),
      featureExtractorArchitecture = document.getMandatory[BsonString]("featureExtractorArchitecture").getValue
    )
  }

  protected def customModelToDocument(custom: CVModelType.Custom): Document = {
    Document(
      "classReference" -> classReferenceToDocument(custom.classReference),
      "labelMode" -> custom.labelMode.map(albumLabelModeToString)
    )
  }

  protected def documentToCustomModel(document: Document): CVModelType.Custom = {
    CVModelType.Custom(
      classReference = documentToClassReference(document.getChildMandatory("classReference")),
      labelMode = document.get[BsonString]("labelMode").map { labelMode =>
        stringToAlbumLabelMode(labelMode.getValue)
      }
    )
  }
}

object CVModelDao {

  case object Name extends Field
  case object Created extends Field
  case object Updated extends Field

  case class CVFeatureExtractorIdIs(feId: String) extends Filter
  case class ExperimentIdIs(experimentId: String) extends Filter
  case class OperatorIdIs(operatorId: String) extends Filter

  def tlConsumerToDocument(consumer: CVModelType.TLConsumer): Document = {
    consumer match {
      case CVModelType.TLConsumer.Classifier(operatorId) => Document(
        "tlType" -> "CLASSIFIER",
        "operatorId" -> BsonString(operatorId)
      )
      case CVModelType.TLConsumer.Localizer(operatorId) => Document(
        "tlType" -> "LOCALIZER",
        "operatorId" -> BsonString(operatorId)
      )
      case CVModelType.TLConsumer.Decoder(operatorId) => Document(
        "tlType" -> "DECODER",
        "operatorId" -> BsonString(operatorId)
      )
    }
  }

  def documentToTLConsumer(document: Document): CVModelType.TLConsumer = {
    val operatorId = document.getMandatory[BsonString]("operatorId").getValue
    document.getMandatory[BsonString]("tlType").getValue match {
      case "CLASSIFIER" => CVModelType.TLConsumer.Classifier(operatorId)
      case "LOCALIZER" => CVModelType.TLConsumer.Localizer(operatorId)
      case "DECODER" => CVModelType.TLConsumer.Decoder(operatorId)
    }
  }

}
