package baile.services.cv.prediction

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.dao.cv.prediction.CVPredictionDao
import baile.daocommons.WithId
import baile.daocommons.filters.Filter
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.cv.prediction.CVPrediction
import baile.domain.cv.prediction.CVPredictionStatus._
import baile.services.asset.SampleNestedUsageChecker
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when

import scala.concurrent.ExecutionContext

class CVPredictionNestedUsageSpec extends BaseSpec { spec =>

  private val dao = mock[CVPredictionDao]

  private val checker = new SampleNestedUsageChecker with CVPredictionNestedUsage {
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
      probabilityPredictionTableId = None,
      evaluationSummary = None,
      predictionTimeSpentSummary = None,
      evaluateTimeSpentSummary = None,
      description = None,
      cvModelPredictOptions = None
    ),
    randomString()
  )

  when(dao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(1))

  "CVPredictionNestedUsage#checkNestedUsage" should {

    "return occupied (left) if input album is used in prediction" in {
      whenReady(
        checker.checkNestedUsage(AssetReference(prediction.entity.inputAlbumId, AssetType.Album), SampleUser).value
      )(_ shouldBe ().asLeft)
    }

    "return occupied (left) if output album is used in prediction" in {
      whenReady(
        checker.checkNestedUsage(AssetReference(prediction.entity.outputAlbumId, AssetType.Album), SampleUser).value
      )(_ shouldBe ().asLeft)
    }

    "return occupied (left) if model is used in prediction" in {
      whenReady(
        checker.checkNestedUsage(AssetReference(prediction.entity.modelId, AssetType.CvModel), SampleUser).value
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

    "return free (right) if album is not used in prediction" in {
      when(dao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(0))
      whenReady(
        checker.checkNestedUsage(
          AssetReference("inputAlbumId", AssetType.Album),
          SampleUser
        ).value
      )(_ shouldBe ().asRight)
    }

    "return free (right) if model is not used in prediction" in {
      when(dao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(0))
      whenReady(
        checker.checkNestedUsage(
          AssetReference("modelId", AssetType.CvModel),
          SampleUser
        ).value
      )(_ shouldBe ().asRight)
    }

  }

}
