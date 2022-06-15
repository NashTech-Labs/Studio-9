package baile.routes.contract.images

import baile.daocommons.WithId
import baile.domain.images.{ Picture, PictureTag }
import baile.routes.contract.images.augmentation._
import play.api.libs.json.{ Json, OWrites }

case class PictureResponse(
  id: String,
  albumId: String,
  filepath: String,
  filename: String,
  filesize: Option[Long],
  caption: Option[String],
  predictedCaption: Option[String],
  tags: Seq[PictureTag],
  predictedTags: Seq[PictureTag],
  originalPictureId: Option[String],
  augmentationsApplied: Option[Seq[PictureAugmentationAppliedResponse]]
)


object PictureResponse {
  implicit val PictureResponseWrites: OWrites[PictureResponse] = Json.writes[PictureResponse]

  def fromDomain(model: WithId[Picture]): PictureResponse =
    PictureResponse(
      id = model.id,
      albumId = model.entity.albumId,
      filepath = model.entity.filePath,
      filename = model.entity.fileName,
      filesize = model.entity.fileSize,
      caption = model.entity.caption,
      predictedCaption = model.entity.predictedCaption,
      tags = model.entity.tags,
      predictedTags = model.entity.predictedTags,
      originalPictureId = model.entity.originalPictureId,
      augmentationsApplied = model.entity.appliedAugmentations.map {
        appliedAugmentations => appliedAugmentations.map(PictureAugmentationAppliedResponse.fromDomain)
      }
    )

}
