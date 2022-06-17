package baile.services.cv.model

import java.time.Instant

import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.cv.model.CVModelDao
import baile.domain.asset.sharing.SharedResource
import baile.domain.asset.{ AssetReference, AssetType }
import baile.services.asset.sharing.SampleSharedAccessChecker
import baile.services.usermanagement.util.TestData
import cats.implicits._

class CVModelSharedAccessSpec extends ExtendedBaseSpec { spec =>

  private val dao = mock[CVModelDao]

  private val checker = new SampleSharedAccessChecker with CVModelSharedAccess {
    override protected val cvModelDao: CVModelDao = dao
  }

  private val featureExtractorId = randomString()

  private val model = CVModelRandomGenerator.randomModel(
    featureExtractorId = Some(featureExtractorId)
  )

  private val sharedModel = SharedResource(
    ownerId = model.entity.ownerId,
    name = Some(model.entity.name),
    created = Instant.now,
    updated = Instant.now,
    recipientId = Some(TestData.SampleUser.id),
    recipientEmail = Some(TestData.SampleUser.email),
    assetType = AssetType.CvModel,
    assetId = model.id
  )

  dao.get(model.id)(*) shouldReturn future(Some(model))

  "CVModelNestedAccess#checkSharedAccess" should {

    "provide access for feature extractor if model is shared" in {
      whenReady(
        checker.checkSharedAccess(AssetReference(featureExtractorId, AssetType.CvModel), sharedModel).value
      )(_ shouldBe ().asRight)
    }

    "return left for unknown asked asset type" in {
      whenReady(
        checker.checkSharedAccess(
          AssetReference(randomString(), randomOf(AssetType.Table, AssetType.Table, AssetType.OnlineJob)),
          sharedModel
        ).value
      )(_ shouldBe ().asLeft)
    }

    "return left for wrong shared resource asset type" in {
      whenReady(
        checker.checkSharedAccess(
          AssetReference(model.entity.featureExtractorId.get, AssetType.CvModel),
          sharedModel.copy(assetType = AssetType.CvPrediction)
        ).value
      )(_ shouldBe ().asLeft)
    }

  }

}
