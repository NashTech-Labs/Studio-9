package baile.routes.contract.asset

import java.time.Instant
import java.util.UUID

import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.asset.sharing.SharedResource
import play.api.libs.json.{ Json, OWrites }

case class SharedResourceResponse(
  id: String,
  ownerId: String,
  name: Option[String] = None,
  created: Instant,
  updated: Instant,
  recipientId: Option[UUID] = None,
  recipientEmail: Option[String] = None,
  assetType: AssetType,
  assetId: String
)

object SharedResourceResponse {
  implicit val SharedResourceResponseWrites: OWrites[SharedResourceResponse] = Json.writes[SharedResourceResponse]

  def fromDomain(in: WithId[SharedResource]): SharedResourceResponse = {
    SharedResourceResponse(
      id = in.id,
      ownerId = in.entity.ownerId.toString,
      name = in.entity.name,
      created = in.entity.created,
      updated = in.entity.updated,
      recipientId = in.entity.recipientId,
      recipientEmail = in.entity.recipientEmail,
      assetType = in.entity.assetType,
      assetId = in.entity.assetId
    )
  }
}
