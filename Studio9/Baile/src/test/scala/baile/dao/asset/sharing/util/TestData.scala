package baile.dao.asset.sharing.util

import java.time.Instant
import java.util.UUID

import baile.domain.asset.AssetType.{ OnlineJob, Table }
import baile.domain.asset.sharing.SharedResource
import org.mongodb.scala.Document

object TestData {

  val OwnerID: UUID = java.util.UUID.randomUUID()
  val RecipientId: UUID = java.util.UUID.randomUUID()

  val SharedResourceEntity: SharedResource = SharedResource(
    ownerId = OwnerID,
    name = Some("name"),
    created = Instant.now(),
    updated = Instant.now(),
    recipientId = Some(RecipientId),
    recipientEmail = Some("recipientEmail"),
    assetType = Table,
    assetId = "assetId"
  )
  val SharedResourceDocument: Document = Document(
    "ownerId" -> SharedResourceEntity.ownerId.toString,
    "name" -> SharedResourceEntity.name,
    "created" -> SharedResourceEntity.created.toString,
    "updated" -> SharedResourceEntity.updated.toString,
    "recipientId" -> SharedResourceEntity.recipientId.map(_.toString),
    "recipientEmail" -> SharedResourceEntity.recipientEmail.toString,
    "assetType" -> SharedResourceEntity.assetType.toString.toUpperCase,
    "assetId" -> SharedResourceEntity.assetId
  )
  val EntityWithNone: SharedResource = SharedResourceEntity.copy(recipientId = None, assetType = OnlineJob,
    recipientEmail = None, name = None)
  val DocumentWithNull: Document = Document(
    "ownerId" -> EntityWithNone.ownerId.toString,
    "name" -> EntityWithNone.name,
    "created" -> EntityWithNone.created.toString,
    "updated" -> EntityWithNone.updated.toString,
    "recipientId" -> EntityWithNone.recipientId.map(_.toString),
    "recipientEmail" -> EntityWithNone.recipientEmail,
    "assetType" -> "ONLINE_JOB",
    "assetId" -> EntityWithNone.assetId
  )

}
