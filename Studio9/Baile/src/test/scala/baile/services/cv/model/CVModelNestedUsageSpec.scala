package baile.services.cv.model

import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.dao.cv.model.CVModelDao
import baile.daocommons.filters.Filter
import baile.domain.asset.{ AssetReference, AssetType }
import baile.services.asset.SampleNestedUsageChecker
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._

import scala.concurrent.ExecutionContext

class CVModelNestedUsageSpec extends ExtendedBaseSpec { spec =>

  private val dao = mock[CVModelDao]

  private val checker = new SampleNestedUsageChecker with CVModelNestedUsage {
    override protected val cvModelDao: CVModelDao = dao
  }

  private val featureExtractorId = randomString()

  dao.count(any[Filter])(any[ExecutionContext]) shouldReturn future(1)

  "CVModelNestedUsage#checkNestedUsage" should {

    "return occupied (left) if feature extractor is used in model" in {
      whenReady(
        checker.checkNestedUsage(AssetReference(featureExtractorId, AssetType.CvModel), SampleUser).value
      )(_ shouldBe ().asLeft)
    }

    "return free (right) for unknown asked asset type" in {
      whenReady(
        checker.checkNestedUsage(
          AssetReference(randomString(), randomOf(AssetType.Table, AssetType.Table, AssetType.OnlineJob)),
          SampleUser
        ).value
      )(_ shouldBe ().asRight)
    }

    "return free (right) if feature extractor is not used in model" in {
      dao.count(any[Filter])(any[ExecutionContext]) shouldReturn future(0)
      whenReady(
        checker.checkNestedUsage(
          AssetReference("feId", AssetType.CvModel),
          SampleUser
        ).value
      )(_ shouldBe ().asRight)
    }
  }

}
