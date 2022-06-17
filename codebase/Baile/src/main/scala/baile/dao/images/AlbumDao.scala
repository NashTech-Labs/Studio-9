package baile.dao.images

import java.time.Instant
import java.util.UUID

import baile.dao.CommonSerializers
import baile.dao.asset.Filters._
import baile.dao.images.AlbumLabelModeSerializers.{ albumLabelModeToString, stringToAlbumLabelMode }
import baile.dao.mongo.BsonHelpers._
import baile.dao.mongo.MongoEntityDao
import baile.daocommons.filters.Filter
import baile.daocommons.sorting.Field
import baile.domain.images.AlbumStatus._
import baile.domain.images.AlbumType.{ Derived, Source, TrainResults }
import baile.domain.images._
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonDocument, BsonInt32, BsonInt64, BsonString }
import org.mongodb.scala.model.{ Filters => MongoFilters }
import org.mongodb.scala.{ Document, MongoDatabase }

import scala.util.Try

object AlbumDao {

  case object Name extends Field
  case object Created extends Field
  case object Updated extends Field

}

class AlbumDao(protected val database: MongoDatabase) extends MongoEntityDao[Album] {

  import baile.dao.images.AlbumDao._

  override val collectionName: String = "albums"

  override protected val fieldMapper: Map[Field, String] =
    Map(
      Name -> "name",
      Created -> "created",
      Updated -> "updated"
    )

  override protected val specificFilterMapper: PartialFunction[Filter, Try[Predicate]] = {
    case OwnerIdIs(ownerId) => Try(MongoFilters.equal("ownerId", ownerId.toString))
    case NameIs(name) => Try(MongoFilters.equal("name", name))
    // TODO add protection from injections to regex
    case SearchQuery(term) => Try(MongoFilters.regex("name", term, "i"))
    case InLibraryIs(inLibrary) => Try(MongoFilters.equal("inLibrary", inLibrary))
  }

  // TODO: status, type, and labelMode need correct serializers
  override protected[images] def entityToDocument(entity: Album): Document = Document(
    "ownerId" -> entity.ownerId.toString,
    "name" -> entity.name,
    "status" -> entity.status.toString.toUpperCase,
    "typez" -> entity.`type`.toString.toUpperCase,
    "labelMode" -> albumLabelModeToString(entity.labelMode),
    "created" -> entity.created.toString,
    "updated" -> entity.updated.toString,
    "inLibrary" -> entity.inLibrary,
    "picturesPrefix" -> entity.picturesPrefix,
    "video" -> entity.video.map(video => videoToDocument(video)),
    "description" -> entity.description.map(BsonString(_)),
    "augmentationTimeSpentSummary" -> entity.augmentationTimeSpentSummary.map(summaryToDocument)
  )

  override protected[images] def documentToEntity(document: Document): Try[Album] = Try {
    Album(
      ownerId = UUID.fromString(document.getMandatory[BsonString]("ownerId").getValue),
      name = document.getMandatory[BsonString]("name").getValue,
      status = convertToAlbumStatus(document.getMandatory[BsonString]("status").getValue),
      `type` = convertToAlbumType(document.getMandatory[BsonString]("typez").getValue),
      labelMode = stringToAlbumLabelMode(document.getMandatory[BsonString]("labelMode").getValue),
      created = Instant.parse(document.getMandatory[BsonString]("created").getValue),
      updated = Instant.parse(document.getMandatory[BsonString]("updated").getValue),
      inLibrary = document.getMandatory[BsonBoolean]("inLibrary").getValue,
      picturesPrefix = document.getMandatory[BsonString]("picturesPrefix").getValue,
      video = document.get[BsonDocument]("video").map(document => documentToVideo(document)),
      description = document.get[BsonString]("description").map(_.getValue),
      augmentationTimeSpentSummary = document.get[BsonDocument]("augmentationTimeSpentSummary").map { summary =>
        documentToSummary(summary)
      }
    )
  }

  private def summaryToDocument(summary: AugmentationTimeSpentSummary): Document = Document(
    "dataFetchTime" -> BsonInt64(summary.dataFetchTime),
    "augmentationTime" -> BsonInt64(summary.augmentationTime),
    "tasksQueuedTime" -> BsonInt64(summary.tasksQueuedTime),
    "totalJobTime" -> BsonInt64(summary.totalJobTime),
    "pipelineTimings" -> BsonArray(summary.pipelineTimings.map(CommonSerializers.pipelineTimingToDocument))
  )

  private def documentToSummary(document: Document): AugmentationTimeSpentSummary = AugmentationTimeSpentSummary(
    dataFetchTime = document.getMandatory[BsonInt64]("dataFetchTime").longValue,
    augmentationTime = document.getMandatory[BsonInt64]("augmentationTime").longValue,
    tasksQueuedTime = document.getMandatory[BsonInt64]("tasksQueuedTime").getValue,
    totalJobTime = document.getMandatory[BsonInt64]("totalJobTime").getValue,
    pipelineTimings = document.getMandatory[BsonArray]("pipelineTimings").map { elem =>
      CommonSerializers.documentToPipelineTiming(elem.asDocument)
    }.toList
  )


  private def convertToAlbumStatus(caseObjectAsString: String): AlbumStatus = {
    caseObjectAsString match {
      case "SAVING" => Saving
      case "UPLOADING" => Uploading
      case "ACTIVE" => Active
      case "FAILED" => Failed
    }
  }

  private def convertToAlbumType(caseObjectAsString: String): AlbumType = {
    caseObjectAsString match {
      case "SOURCE" => Source
      case "DERIVED" => Derived
      case "TRAINRESULTS" => TrainResults
    }
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

  private def documentToVideo(document: Document): Video = {
    Video(
      filePath = document.getMandatory[BsonString]("filePath").getValue,
      fileSize = document.getMandatory[BsonInt64]("fileSize").getValue,
      fileName = document.getMandatory[BsonString]("fileName").getValue,
      frameRate = document.getMandatory[BsonInt32]("frameRate").getValue,
      frameCaptureRate = document.getMandatory[BsonInt32]("frameCaptureRate").getValue,
      height = document.getMandatory[BsonInt32]("height").getValue,
      width = document.getMandatory[BsonInt32]("width").getValue
    )
  }

}
