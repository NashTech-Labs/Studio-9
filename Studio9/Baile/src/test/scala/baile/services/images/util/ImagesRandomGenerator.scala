package baile.services.images.util

import java.time.Instant
import java.util.UUID

import baile.RandomGenerators._
import baile.daocommons.WithId
import baile.domain.images._

object ImagesRandomGenerator {

  def randomAlbum(
    status: AlbumStatus = randomOf(
      AlbumStatus.Saving,
      AlbumStatus.Active,
      AlbumStatus.Failed,
      AlbumStatus.Uploading
    ),
    labelMode: AlbumLabelMode = randomOf(
      AlbumLabelMode.Localization,
      AlbumLabelMode.Classification
    ),
    name: String = randomString(),
    ownerId: UUID = UUID.randomUUID(),
    inLibrary: Boolean = randomBoolean(),
    id: String = randomString()
  ): WithId[Album] =
    WithId(
      Album(
        ownerId = ownerId,
        name = name,
        created = Instant.now(),
        updated = Instant.now(),
        status = status,
        `type` = randomOf(AlbumType.Source, AlbumType.TrainResults, AlbumType.Derived),
        labelMode = labelMode,
        inLibrary = inLibrary,
        picturesPrefix = randomString(),
        video = None,
        description = None,
        augmentationTimeSpentSummary = None
      ),
      id
    )

  def randomPicture(): WithId[Picture] = WithId(
    Picture(
      albumId = randomString(),
      filePath = randomString(),
      fileName = randomString(),
      fileSize = Some(10),
      caption = None,
      predictedCaption = None,
      tags = Seq.empty,
      predictedTags = Seq.empty,
      meta = Map.empty,
      originalPictureId = None,
      appliedAugmentations = None
    ),
    randomString()
  )

}
