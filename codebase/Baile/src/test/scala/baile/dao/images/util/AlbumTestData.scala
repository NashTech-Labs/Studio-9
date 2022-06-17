package baile.dao.images.util

import java.time.Instant
import java.util.UUID

import baile.domain.images.AlbumLabelMode.{ Classification, Localization }
import baile.domain.images.AlbumStatus._
import baile.domain.images.AlbumType.{ Derived, Source, TrainResults }
import baile.domain.images.{ Album, AugmentationTimeSpentSummary, Video }
import baile.domain.job.PipelineTiming
import org.mongodb.scala.Document
import org.mongodb.scala.bson.{ BsonDocument, BsonInt64, BsonNull, BsonString }

object AlbumTestData {


  val TestDate: Instant = Instant.now()
  val PipelineTimings = List(PipelineTiming("step1", 20l), PipelineTiming("step1", 20l))
  val AugmentationTimeSpentSummarySample = AugmentationTimeSpentSummary(10l, 10l, 10l, 10l, PipelineTimings)
  val AlbumEntity = Album(
    ownerId = UUID.randomUUID,
    name = "name",
    status = Active,
    `type` = TrainResults,
    labelMode = Classification,
    created = TestDate,
    updated = TestDate,
    inLibrary = false,
    picturesPrefix = "pic",
    video = Some(Video("filePath", 1l, "fileName", 0, 0, 0, 0)),
    description = None,
    augmentationTimeSpentSummary = Some(AugmentationTimeSpentSummarySample)
  )
  val AlbumWithFDL: Album = AlbumEntity.copy(status = Failed, `type` = Derived, labelMode = Localization)
  val AlbumWithUSC: Album = AlbumEntity.copy(status = Uploading, `type` = Source)
  val AlbumWithSTC: Album = AlbumEntity.copy(status = Saving)
  val AlbumEntityWithNone = Album(
    ownerId = UUID.randomUUID,
    name = "name",
    status = Active,
    `type` = TrainResults,
    labelMode = Classification,
    created = TestDate,
    updated = TestDate,
    inLibrary = false,
    picturesPrefix = "pic",
    description = None,
    augmentationTimeSpentSummary = None

  )
  val AlbumWithUSCDoc = Document(
    "ownerId" -> AlbumWithUSC.ownerId.toString,
    "name" -> AlbumWithUSC.name,
    "status" -> AlbumWithUSC.status.toString.toUpperCase,
    "typez" -> AlbumWithUSC.`type`.toString.toUpperCase,
    "labelMode" -> AlbumWithUSC.labelMode.toString.toUpperCase,
    "created" -> AlbumWithUSC.created.toString,
    "updated" -> AlbumWithUSC.updated.toString,
    "inLibrary" -> AlbumWithUSC.inLibrary,
    "picturesPrefix" -> AlbumWithUSC.picturesPrefix,
    "video" -> AlbumWithUSC.video.map(video => videoToDocument(video)),
    "description" -> BsonNull(),
    "augmentationTimeSpentSummary" -> AlbumEntity.augmentationTimeSpentSummary.map(summaryToDocument)
  )
  val AlbumWithFDLDoc = Document(
    "ownerId" -> AlbumWithFDL.ownerId.toString,
    "name" -> AlbumWithFDL.name,
    "status" -> AlbumWithFDL.status.toString.toUpperCase,
    "typez" -> AlbumWithFDL.`type`.toString.toUpperCase,
    "labelMode" -> AlbumWithFDL.labelMode.toString.toUpperCase,
    "created" -> AlbumWithFDL.created.toString,
    "updated" -> AlbumWithFDL.updated.toString,
    "inLibrary" -> AlbumWithFDL.inLibrary,
    "picturesPrefix" -> AlbumWithFDL.picturesPrefix,
    "video" -> AlbumWithFDL.video.map(video => videoToDocument(video)),
    "description" -> BsonNull(),
    "augmentationTimeSpentSummary" -> AlbumEntity.augmentationTimeSpentSummary.map(summaryToDocument)
  )
  val AlbumWithSTCDoc = Document(
    "ownerId" -> AlbumWithSTC.ownerId.toString,
    "name" -> AlbumWithSTC.name,
    "status" -> AlbumWithSTC.status.toString.toUpperCase,
    "typez" -> AlbumWithSTC.`type`.toString.toUpperCase,
    "labelMode" -> AlbumWithSTC.labelMode.toString.toUpperCase,
    "created" -> AlbumWithSTC.created.toString,
    "updated" -> AlbumWithSTC.updated.toString,
    "inLibrary" -> AlbumWithSTC.inLibrary,
    "picturesPrefix" -> AlbumWithSTC.picturesPrefix,
    "video" -> AlbumWithSTC.video.map(video => videoToDocument(video)),
    "description" -> BsonNull(),
    "augmentationTimeSpentSummary" -> AlbumEntity.augmentationTimeSpentSummary.map(summaryToDocument)
  )
  val AlbumDoc = Document(
    "ownerId" -> AlbumEntity.ownerId.toString,
    "name" -> AlbumEntity.name,
    "status" -> AlbumEntity.status.toString.toUpperCase,
    "typez" -> AlbumEntity.`type`.toString.toUpperCase,
    "labelMode" -> AlbumEntity.labelMode.toString.toUpperCase,
    "created" -> AlbumEntity.created.toString,
    "updated" -> AlbumEntity.updated.toString,
    "inLibrary" -> AlbumEntity.inLibrary,
    "picturesPrefix" -> AlbumEntity.picturesPrefix,
    "video" -> AlbumEntity.video.map(video => videoToDocument(video)),
    "description" -> None,
    "augmentationTimeSpentSummary" -> AlbumEntity.augmentationTimeSpentSummary.map(summaryToDocument)
  )
  val AlbumDocWithNone = {
    Document(
      "ownerId" -> AlbumEntityWithNone.ownerId.toString,
      "name" -> AlbumEntityWithNone.name,
      "status" -> AlbumEntityWithNone.status.toString.toUpperCase,
      "typez" -> AlbumEntityWithNone.`type`.toString.toUpperCase,
      "labelMode" -> AlbumEntityWithNone.labelMode.toString.toUpperCase,
      "created" -> AlbumEntityWithNone.created.toString,
      "updated" -> AlbumEntityWithNone.updated.toString,
      "inLibrary" -> AlbumEntityWithNone.inLibrary,
      "picturesPrefix" -> AlbumEntityWithNone.picturesPrefix,
      "video" -> AlbumEntityWithNone.video.map(video => videoToDocument(video)),
      "description" -> BsonNull(),
      "augmentationTimeSpentSummary" -> BsonNull()
    )
  }

  private def videoToDocument(video: Video): Document = {
    Document(
      "filePath" -> video.filePath,
      "fileSize" -> video.fileSize,
      "fileName" -> video.fileName,
      "frameRate" -> video.frameRate,
      "frameCaptureRate" -> video.frameCaptureRate,
      "height" -> video.height,
      "width" -> video.width
    )
  }

  private def summaryToDocument(summary: AugmentationTimeSpentSummary): Document = {
    Document(
      "dataFetchTime" -> summary.dataFetchTime,
      "augmentationTime" -> summary.augmentationTime,
      "totalJobTime" -> summary.totalJobTime,
      "tasksQueuedTime" -> summary.tasksQueuedTime,
      "pipelineTimings" -> summary.pipelineTimings.map { pipelineTiming =>
        BsonDocument(
          "description" -> BsonString(pipelineTiming.description),
          "time" -> BsonInt64(pipelineTiming.time)
        )
      }
    )
  }
}
