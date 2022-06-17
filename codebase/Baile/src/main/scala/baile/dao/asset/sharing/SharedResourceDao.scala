package baile.dao.asset.sharing

import java.time.Instant
import java.util.UUID

import baile.dao.CommonSerializers
import baile.dao.mongo.BsonHelpers.DocumentExtensions
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.asset.AssetType
import baile.domain.asset.sharing.SharedResource
import org.bson.BsonString
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object SharedResourceDao {

  case class OwnerIdIs(ownerId: UUID) extends Filter
  case class NameIs(name: String) extends Filter
  case class AssetIdIs(assetId: String) extends Filter
  case class AssetTypeIs(assetType: AssetType) extends Filter
  case class RecipientIdIs(recipientId: UUID) extends Filter
  case class RecipientEmailIs(email: String) extends Filter

  case object Name extends Field
  case object Updated extends Field
  case object Created extends Field

}

class SharedResourceDao(protected val database: MongoDatabase) extends MongoEntityDao[SharedResource] {

  import SharedResourceDao._

  override val collectionName: String = "sharedResources"

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case OwnerIdIs(ownerId) =>
      Try(MongoFilters.equal("ownerId", ownerId.toString))
    case NameIs(name) =>
      Try(MongoFilters.equal("name", name))
    case AssetIdIs(assetId) =>
      Try(MongoFilters.equal("assetId", assetId))
    case AssetTypeIs(assetType) =>
      Try(MongoFilters.equal("assetType", CommonSerializers.assetTypeToString(assetType)))
    case RecipientIdIs(recipientId) =>
      Try(MongoFilters.equal("recipientId", recipientId.toString))
    case RecipientEmailIs(email) =>
      Try(MongoFilters.equal("recipientEmail", email))

  }

  override protected[sharing] def entityToDocument(entity: SharedResource): Document = Document(
    "ownerId" -> entity.ownerId.toString,
    "name" -> entity.name,
    "created" -> entity.created.toString,
    "updated" -> entity.updated.toString,
    "recipientId" -> entity.recipientId.map(_.toString),
    "recipientEmail" -> entity.recipientEmail,
    "assetType" -> CommonSerializers.assetTypeToString(entity.assetType),
    "assetId" -> entity.assetId
  )

  override protected[sharing] def documentToEntity(document: Document): Try[SharedResource] = Try {
    SharedResource(
      ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
      assetId = document.getMandatory[BsonString]("assetId").getValue,
      name = document.get[BsonString]("name").map(value => value.getValue),
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
      updated = Instant.parse(document.getMandatory[BsonString]("updated").getValue),
      recipientId = document.get[BsonString]("recipientId").map(value => UUID.fromString(value.getValue)),
      recipientEmail = document.get[BsonString]("recipientEmail").map(value => value.getValue),
      assetType = CommonSerializers.assetTypeFromString(document.getMandatory[BsonString]("assetType").getValue)
    )
  }

  override protected val fieldMapper: Map[Field, String] =
    Map(
      Name -> "name",
      Updated -> "updated",
      Created -> "created"
    )

}
