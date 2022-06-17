package baile.dao.tabular.prediction

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.domain.tabular.prediction.{ ColumnMapping, TabularPrediction, TabularPredictionStatus }
import org.mongodb.scala.MongoDatabase

import scala.util.Success

class TabularPredictionDaoSpec extends BaseSpec {

  "TabularPredictionDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val dao: TabularPredictionDao = new TabularPredictionDao(mockedMongoDatabase)

    val prediction = TabularPrediction(
      ownerId = UUID.randomUUID,
      name = "tabular prediction",
      created = Instant.now(),
      updated = Instant.now(),
      status = TabularPredictionStatus.Running,
      modelId = "id",
      inputTableId = "in",
      outputTableId = "out",
      columnMappings = Seq(
        ColumnMapping(
          trainName = "category",
          currentName = "types"
        )
      ),
      description = None
    )

    "convert tabular prediction to document and back" in {
      val document = dao.entityToDocument(prediction)
      val restoredTabularPrediction = dao.documentToEntity(document)

      restoredTabularPrediction shouldBe Success(prediction)
    }
  }

}
