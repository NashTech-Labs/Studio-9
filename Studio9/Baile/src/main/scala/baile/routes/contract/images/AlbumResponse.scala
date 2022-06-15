package baile.routes.contract.images

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.images.{ Album, AlbumLabelMode, AlbumStatus, AlbumType }
import play.api.libs.json.{ Json, OWrites }

case class AlbumResponse(
  id: String,
  ownerId: String,
  name: String,
  created: Instant,
  updated: Instant,
  status: AlbumStatus,
  inLibrary: Boolean,

  `type`: AlbumType,
  locked: Boolean,
  labelMode: AlbumLabelMode,
  video: Option[AlbumVideoResponse],
  description: Option[String],
  augmentationTimeSpentSummary: Option[AugmentationTimeSpentSummaryResponse]
)

object AlbumResponse {
  implicit val AlbumResponseWrites: OWrites[AlbumResponse] = Json.writes[AlbumResponse]

  def fromDomain(in: WithId[Album]): AlbumResponse = in match {
    case WithId(album, id) => AlbumResponse(
      id = id,
      ownerId = album.ownerId.toString,
      name = album.name,
      created = album.created,
      updated = album.updated,
      status = album.status,
      inLibrary = album.inLibrary,
      `type` = album.`type`,
      locked = false,
      labelMode = album.labelMode,
      video = album.video.map(AlbumVideoResponse.fromDomain),
      description = album.description,
      augmentationTimeSpentSummary = album.augmentationTimeSpentSummary
        .map(AugmentationTimeSpentSummaryResponse.fromDomain)
    )
  }
}
