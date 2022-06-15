package baile.routes.asset.sharing.util

import java.time.Instant
import java.util.UUID

import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.asset.AssetType.Table
import baile.domain.asset.sharing.SharedResource
import baile.routes.contract.asset.SharedResourceRequest
import play.api.libs.json.{ JsObject, JsString, Json }
import baile.services.usermanagement.util.TestData._

object TestData {

  val Id: String = "id"
  val DateTime: Instant = Instant.now()

  val OwnerID: UUID = UUID.fromString("e4475008-a1b0-4e22-9103-633bd1f1b437")
  val RecipientId: UUID = UUID.fromString("e4475008-a1b0-4e22-9103-633bd1f1b437")

  val SharedResourceRequestEntity = SharedResourceRequest(
    Some("assertName"),
    Some(RecipientId),
    Some("recipientEmail"), Table, "assertId"
  )

  val SharedResourceEntity: SharedResource = SharedResource(
    ownerId = OwnerID,
    name = Some("name"),
    created = DateTime,
    updated = DateTime,
    recipientId = Some(RecipientId),
    recipientEmail = Some("recipientEmail"),
    assetType = Table,
    assetId = "assetId"
  )
  val AssetId: String = "assetId"
  val AssetType: AssetType = Table

  val SharedResourceWithId = WithId(SharedResourceEntity, Id)
  val SharedResourceRequestAsJson: String =
    """{
      |  "name":"assertName",
      |  "recipientId":"e4475008-a1b0-4e22-9103-633bd1f1b437",
      |  "recipientEmail":"recipientEmail",
      |  "assetType":"TABLE",
      |  "assetId":"assertId"
      |}""".stripMargin

  val SharedResourceResponseData: JsObject = Json.obj(
    "id" -> JsString(Id),
    "ownerId" -> JsString(SharedResourceEntity.ownerId.toString),
    "name" -> JsString(SharedResourceEntity.name.get),
    "created" -> JsString(SharedResourceEntity.created.toString),
    "updated" -> JsString(SharedResourceEntity.updated.toString),
    "recipientId" -> JsString(SharedResourceEntity.recipientId.get.toString),
    "recipientEmail" -> JsString(SharedResourceEntity.recipientEmail.get),
    "assetType" -> JsString("TABLE"),
    "assetId" -> JsString(SharedResourceEntity.assetId)
  )

  val UserResponseData: JsObject = Json.obj(
    "id" -> JsString(SampleUser.id.toString),
    "email" -> JsString("jd@example.com"),
    "firstName" -> JsString("John"),
    "lastName" -> JsString("Doe")
  )

}
