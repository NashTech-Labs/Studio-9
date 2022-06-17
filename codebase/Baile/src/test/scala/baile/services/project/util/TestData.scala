package baile.services.project.util

import java.time.{ Instant, ZonedDateTime }

import baile.daocommons.WithId
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.cv.prediction.{ CVPrediction, CVPredictionStatus }
import baile.domain.images.AlbumStatus.Saving
import baile.domain.images.{ Album, AlbumLabelMode, AlbumType }
import baile.domain.project.{ Folder, Project, ProjectAssetReference }
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.cv.model.CVModelRandomGenerator

object TestData {

  val Now = Instant.now()
  val DateAndTime = ZonedDateTime.now

  val FolderSample = Folder(
    "/path/to/folder"
  )

  val ProjectSample = Project(
    name = "name",
    created = Now,
    updated = Now,
    ownerId = SampleUser.id,
    folders = Seq.empty,
    assets = Seq(
      ProjectAssetReference(AssetReference("cvModelId", AssetType.CvModel), None),
      ProjectAssetReference(AssetReference("cvPrediction", AssetType.CvPrediction), None),
      ProjectAssetReference(AssetReference("albumId", AssetType.Album), None),
      ProjectAssetReference(AssetReference("flowId", AssetType.Flow), None),
      ProjectAssetReference(AssetReference("tableId", AssetType.Table), None)
    )
  )
  val ProjectWithIdSample = WithId(ProjectSample, "ProjectId")
  val AlbumEntity = Album(
    ownerId = SampleUser.id,
    name = "name",
    status = Saving,
    `type` = AlbumType.Source,
    labelMode = AlbumLabelMode.Classification,
    created = Now,
    updated = Now,
    inLibrary = false,
    picturesPrefix = "picturesPrefix",
    description = None,
    augmentationTimeSpentSummary = None
  )
  val CVModelEntityWithId = CVModelRandomGenerator.randomModel()

  val CVPredictionSample = CVPrediction(
    ownerId = SampleUser.id,
    name = "name",
    status = CVPredictionStatus.Running,
    created = Now,
    updated = Now,
    modelId = "modelId",
    inputAlbumId = "input",
    outputAlbumId = "output",
    evaluationSummary = None,
    probabilityPredictionTableId = None,
    predictionTimeSpentSummary = None,
    evaluateTimeSpentSummary = None,
    description = None,
    cvModelPredictOptions = None
  )

  val CVPredictionWithIdEntity = WithId(CVPredictionSample, "id")
  val AlbumEntityWithId = WithId(AlbumEntity, "albumId")

}
