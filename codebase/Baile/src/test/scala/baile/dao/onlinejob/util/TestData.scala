package baile.dao.onlinejob.util

import java.time.Instant

import baile.domain.onlinejob.{ OnlineJob, OnlineJobStatus, OnlinePredictionOptions }
import baile.domain.usermanagement.User
import baile.services.usermanagement.util.TestData.SampleUser
import org.mongodb.scala.Document
import org.mongodb.scala.bson.BsonString

object TestData {

  val StreamId = "streamId"
  val ModelId = "modelId"
  val BucketId = "bucketId"
  val InputImagesPath = "inputImagesPath"
  val OutputAlbumId = "outputAlbumId"
  val User: User = SampleUser

  val OnlineJobEntity: OnlineJob = OnlineJob(
    ownerId = User.id,
    name = "name",
    status = OnlineJobStatus.Running,
    options = OnlinePredictionOptions(StreamId, ModelId, BucketId, InputImagesPath, OutputAlbumId),
    enabled = true,
    created = Instant.now(),
    updated = Instant.now(),
    description = Some("Job description")
  )

  val OnlineJobDocument: Document = Document(
    "ownerId" -> OnlineJobEntity.ownerId.toString,
    "name" -> OnlineJobEntity.name,
    "status" -> BsonString("RUNNING"),
    "options" -> Document(
      "streamId" -> StreamId,
      "modelId" -> ModelId,
      "bucketId" -> BucketId,
      "inputImagesPath" -> InputImagesPath,
      "outputAlbumId" -> OutputAlbumId
    ),
    "enabled" -> OnlineJobEntity.enabled,
    "created" -> OnlineJobEntity.created.toString,
    "updated" -> OnlineJobEntity.updated.toString,
    "description" -> OnlineJobEntity.description
  )

}
