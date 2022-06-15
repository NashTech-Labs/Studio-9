package baile.services.tabular.prediction

import java.time.Instant

import baile.BaseSpec
import baile.dao.tabular.prediction.TabularPredictionDao
import baile.daocommons.WithId
import baile.daocommons.filters.Filter
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.tabular.prediction.{ ColumnMapping, TabularPrediction, TabularPredictionStatus }
import baile.services.asset.SampleNestedUsageChecker
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when

import scala.concurrent.ExecutionContext

class TabularPredictionNestedUsageSpec extends BaseSpec { spec =>

  private val dao = mock[TabularPredictionDao]

  private val checker = new SampleNestedUsageChecker with TabularPredictionNestedUsage {
    override protected val tabularPredictionDao: TabularPredictionDao = dao
  }

  private val prediction = WithId(
    TabularPrediction(
      ownerId = SampleUser.id,
      name = "predict-name",
      created = Instant.now(),
      updated = Instant.now(),
      status = TabularPredictionStatus.Running,
      modelId = "model-id",
      inputTableId = "input-table",
      outputTableId = "output-id",
      columnMappings = Seq(
        ColumnMapping(
          trainName = "output",
          currentName = "input"
        )
      ),
      description = None
    ),
    "id"
  )

  when(dao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(1))

  "TabularPredictionNestedUsage#checkNestedUsage" should {

    "return occupied (left) if input table is used in prediction" in {
      whenReady(
        checker.checkNestedUsage(AssetReference(prediction.entity.inputTableId, AssetType.Table), SampleUser).value
      )(_ shouldBe ().asLeft)
    }

    "return occupied (left) if output table is used in prediction" in {
      whenReady(
        checker.checkNestedUsage(AssetReference(prediction.entity.outputTableId, AssetType.Table), SampleUser).value
      )(_ shouldBe ().asLeft)
    }

    "return occupied (left) if model is used in prediction" in {
      whenReady(
        checker.checkNestedUsage(AssetReference(prediction.entity.modelId, AssetType.TabularModel), SampleUser).value
      )(_ shouldBe ().asLeft)
    }

    "return free (right) for unknown asked asset type" in {
      whenReady(
        checker.checkNestedUsage(
          AssetReference(randomString(), randomOf(AssetType.CvModel, AssetType.Album, AssetType.OnlineJob)),
          SampleUser
        ).value
      )(_ shouldBe ().asRight)
    }

    "return free (right) if table is not used in prediction" in {
      when(dao.count(any[Filter])(any[ExecutionContext])).thenReturn(future(0))
      whenReady(
        checker.checkNestedUsage(AssetReference("inputTableId", AssetType.Table), SampleUser).value
      )(_ shouldBe ().asRight)
    }

  }

}
