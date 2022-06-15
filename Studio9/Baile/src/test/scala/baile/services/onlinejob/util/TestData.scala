package baile.services.onlinejob.util

import java.time.{ Instant, ZonedDateTime }
import java.util.{ Date, UUID }

import baile.daocommons.WithId
import baile.domain.common.CortexModelReference
import baile.domain.common.S3Bucket.AccessOptions
import baile.domain.cv.model._
import baile.domain.images.AlbumLabelMode.Classification
import baile.domain.images.AlbumStatus.Active
import baile.domain.images.AlbumType.TrainResults
import baile.domain.images.{ Album, Video }
import baile.domain.onlinejob.{ OnlineJob, OnlineJobStatus, OnlinePredictionOptions }
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.onlinejob.OnlinePredictionCreateOptions
import cortex.api.argo.ConfigSetting

object TestData {

  val SampleDate: Date = new Date()
  val SampleInstant: Instant = Instant.now()
  val DateAndTime = ZonedDateTime.now
  val ConfigSettingSample = ConfigSetting(
    serviceName = "serviceName",
    settingName = "settingName",
    settingValue = "settingValue",
    tags = Seq("tags"),
    createdAt = SampleDate,
    updatedAt = SampleDate
  )
  val OnlinePredictionCreateOptionsSample = OnlinePredictionCreateOptions(
    modelId = "modelId",
    bucketId = "bucketId",
    inputImagesPath = "inputImagesPath",
    outputAlbumName = "outputAlbumName"
  )
  val AccessOptionsSample = AccessOptions(
    region = "eu-west-1",
    bucketName = "bucketName",
    accessKey = Some("accessKey"),
    secretKey = Some("secretKey"),
    sessionToken = None
  )
  val CVModelSample = WithId(CVModel(
    ownerId = SampleUser.id,
    name = "model",
    created = SampleInstant,
    updated = SampleInstant,
    status = CVModelStatus.Active,
    cortexFeatureExtractorReference = None,
    cortexModelReference = Some(CortexModelReference(
      cortexId = "cortexId",
      cortexFilePath = "cortexFilePath"
    )),
    `type` = CVModelType.TL(CVModelType.TLConsumer.Classifier("FCN_1"), "SCAE"),
    classNames = Some(Seq("foo")),
    featureExtractorId = Some("feId"),
    description = None,
    inLibrary = false,
    experimentId = None
  ), "id")

  val AlbumSample = WithId(Album(
    ownerId = UUID.randomUUID,
    name = "name",
    status = Active,
    `type` = TrainResults,
    labelMode = Classification,
    created = SampleInstant,
    updated = SampleInstant,
    inLibrary = false,
    picturesPrefix = "pic",
    video = Some(Video("filePath", 1l, "fileName", 0, 0, 0, 0)),
    description = None,
    augmentationTimeSpentSummary = None
  ), "albumId")
  val OnlinePredictionOptionsSample = OnlinePredictionOptions(
    streamId = "8e25ced2-8fe4-4eae-8754-189a9af94f4a",
    modelId = "modelId",
    bucketId = "bucketId",
    inputImagesPath = "inputImagesPath",
    outputAlbumId = "albumId"
  )
  val OnlineJobSample = OnlineJob(
    ownerId = SampleUser.id,
    name = "name",
    status = OnlineJobStatus.Running,
    options = OnlinePredictionOptionsSample,
    enabled = true,
    created = SampleInstant,
    updated = SampleInstant,
    description = None
  )
}
