package baile.services.cv.prediction

import java.time.Instant
import java.util.UUID

import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.cv.prediction.CVPredictionDao
import baile.daocommons.WithId
import baile.domain.asset.sharing.SharedResource
import baile.services.asset.sharing.SampleSharedAccessChecker
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.cv.prediction.CVPredictionStatus._
import baile.domain.cv.prediction.CVPrediction
import baile.services.usermanagement.util.TestData
import cats.implicits._

class CVPredictionSharedAccessSpec extends ExtendedBaseSpec { spec =>

  private val dao = mock[CVPredictionDao]

  private val checker = new SampleSharedAccessChecker with CVPredictionSharedAccess {
    override protected val cvPredictionDao: CVPredictionDao = dao
  }

  private val prediction = WithId(
    CVPrediction(
      ownerId = UUID.randomUUID,
      name = randomString(),
      status = randomOf(Running, Error, Done, New),
      created = Instant.now,
      updated = Instant.now,
      modelId = randomString(),
      inputAlbumId = randomString(),
      outputAlbumId = randomString(),
      evaluationSummary = None,
      predictionTimeSpentSummary = None,
      evaluateTimeSpentSummary = None,
      description = None,
      probabilityPredictionTableId = None,
      cvModelPredictOptions = None
    ),
    randomString()
  )

  private val sharedPrediction = SharedResource(
    ownerId = prediction.entity.ownerId,
    name = Some(prediction.entity.name),
    created = Instant.now,
    updated = Instant.now,
    recipientId = Some(TestData.SampleUser.id),
    recipientEmail = Some(TestData.SampleUser.email),
    assetType = AssetType.CvPrediction,
    assetId = prediction.id
  )

  dao.get(prediction.id)(*) shouldReturn future(Some(prediction))

  "CVPredictionNestedAccess#checkSharedAccess" should {

    "provide access for input album if prediction is shared" in {
      whenReady(
        checker.checkSharedAccess(
          AssetReference(prediction.entity.inputAlbumId, AssetType.Album),
          sharedPrediction
        ).value
      )(_ shouldBe ().asRight)
    }

    "provide access for output album if prediction is shared" in {
      whenReady(
        checker.checkSharedAccess(
          AssetReference(prediction.entity.outputAlbumId, AssetType.Album),
          sharedPrediction
        ).value
      )(_ shouldBe ().asRight)
    }

    "provide access for shared model" in {
      whenReady(
        checker.checkSharedAccess(AssetReference(prediction.entity.modelId, AssetType.CvModel), sharedPrediction).value
      )(_ shouldBe ().asRight)
    }

    "return left for unknown asked asset type" in {
      whenReady(
        checker.checkSharedAccess(
          AssetReference(randomString(), randomOf(AssetType.Table, AssetType.CvModel, AssetType.OnlineJob)),
          sharedPrediction
        ).value
      )(_ shouldBe ().asLeft)
    }

    "return left for wrong shared resource asset type" in {
      whenReady(
        checker.checkSharedAccess(
          AssetReference(prediction.entity.inputAlbumId, AssetType.Album),
          sharedPrediction.copy(assetType = AssetType.CvModel)
        ).value
      )(_ shouldBe ().asLeft)
    }

  }

}
