package baile.services.onlinejob

import java.time.Instant
import java.util.UUID

import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.onlinejob.OnlineJobDao
import baile.daocommons.WithId
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.asset.sharing.SharedResource
import baile.domain.onlinejob.{ OnlineJob, OnlineJobStatus, OnlinePredictionOptions }
import baile.services.asset.sharing.SampleSharedAccessChecker
import baile.services.usermanagement.util.TestData
import cats.implicits._

class OnlineJobSharedAccessSpec extends ExtendedBaseSpec {
  spec =>

  private val dao = mock[OnlineJobDao]

  private val checker = new SampleSharedAccessChecker with OnlineJobSharedAccess {
    override protected val onlineJobDao: OnlineJobDao = dao
  }

  private val onlineJobPredictionOptions = OnlinePredictionOptions(
    streamId = randomString(),
    modelId = randomString(),
    bucketId = randomString(),
    inputImagesPath = randomString(),
    outputAlbumId = randomString()
  )

  private val job = WithId(
    OnlineJob(
      ownerId = UUID.randomUUID(),
      name = randomString(),
      status = OnlineJobStatus.Running,
      options = onlineJobPredictionOptions,
      enabled = true,
      created = Instant.now,
      updated = Instant.now,
      description = None
    ),
    randomString()
  )

  private val sharedJob = SharedResource(
    ownerId = job.entity.ownerId,
    name = Some(job.entity.name),
    created = Instant.now,
    updated = Instant.now,
    recipientId = Some(TestData.SampleUser.id),
    recipientEmail = Some(TestData.SampleUser.email),
    assetType = AssetType.OnlineJob,
    assetId = job.id
  )

  dao.get(job.id)(*) shouldReturn future(Some(job))

  "OnlineJobNestedAccess#checkSharedAccess" should {

    "provide access for album if job is shared" in {
      whenReady(
        checker.checkSharedAccess(job.entity.options.target, sharedJob).value
      )(_ shouldBe ().asRight)
    }

    "return left for unknown asked asset type" in {
      whenReady(
        checker.checkSharedAccess(
          AssetReference(randomString(), randomOf(AssetType.Table, AssetType.Table, AssetType.OnlineJob)),
          sharedJob
        ).value
      )(_ shouldBe ().asLeft)
    }

    "return left for wrong shared resource asset type" in {
      whenReady(
        checker.checkSharedAccess(
          job.entity.options.target,
          sharedJob.copy(assetType = AssetType.CvPrediction)
        ).value
      )(_ shouldBe ().asLeft)
    }

  }

}
