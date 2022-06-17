package baile.routes.contract.images

import baile.domain.images.{ Picture, PictureTag }
import play.api.libs.json.{ Json, Reads }

case class PictureMetaRequest(
  filepath: String,
  filename: String,
  filesize: Option[Long],
  caption: Option[String],
  tags: Seq[PictureTag]
) {

  def toDomain(albumId: String): Picture =
    Picture(
      albumId = albumId,
      filePath = filepath,
      fileName = filename,
      caption = caption,
      fileSize = filesize,
      predictedCaption = None,
      tags = tags,
      predictedTags = Seq.empty[PictureTag],
      meta = Map.empty[String, String],
      originalPictureId = None,
      appliedAugmentations = None
    )

}

object PictureMetaRequest {

  implicit val PictureMetaRequestReads: Reads[PictureMetaRequest] = Json.reads[PictureMetaRequest]

}
