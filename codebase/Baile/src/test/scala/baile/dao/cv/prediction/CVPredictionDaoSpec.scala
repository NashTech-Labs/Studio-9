package baile.dao.cv.prediction

import java.time.Instant
import java.util.UUID

import baile.ExtendedBaseSpec
import baile.RandomGenerators._
import baile.domain.common.ConfusionMatrixCell
import baile.domain.cv.model.CVModelSummary
import baile.domain.cv.prediction.CVPredictionStatus._
import baile.domain.cv.prediction.{ CVModelPredictOptions, CVPrediction, PredictionTimeSpentSummary }
import baile.domain.cv.{ EvaluateTimeSpentSummary, LabelOfInterest }
import baile.domain.job.PipelineTiming
import org.mongodb.scala.MongoDatabase

import scala.util.Success

class CVPredictionDaoSpec extends ExtendedBaseSpec {

  val mongoDatabase: MongoDatabase = mock[MongoDatabase]
  val dao: CVPredictionDao = new CVPredictionDao(mongoDatabase)

  "CVPredictionDao" should {
    "convert cv prediction to document and back" in {

      val pipelineTimings = List(PipelineTiming("step1", 20l), PipelineTiming("step1", 20l))

      val prediction = CVPrediction(
        ownerId = UUID.randomUUID(),
        modelId = "modelId",
        name = "name",
        inputAlbumId = "input",
        outputAlbumId = "output",
        status = randomOf(New, Running, Error, Done),
        created = Instant.now(),
        updated = Instant.now(),
        probabilityPredictionTableId = Some(randomString()),
        evaluationSummary = Some(
          CVModelSummary(
            labels = Seq("foo", "bar"),
            confusionMatrix = Some(Seq(
              ConfusionMatrixCell(Some(0), Some(0), 10),
              ConfusionMatrixCell(Some(0), Some(1), 2)
            )),
            mAP = None,
            reconstructionLoss = None
          )
        ),
        predictionTimeSpentSummary = Some(
          PredictionTimeSpentSummary(
            dataFetchTime = 10l,
            loadModelTime = 10l,
            predictionTime = 10l,
            tasksQueuedTime = 10l,
            totalJobTime = 10l,
            pipelineTimings = pipelineTimings
          )
        ),
        evaluateTimeSpentSummary = Some(
          EvaluateTimeSpentSummary(
            dataFetchTime = 10l,
            loadModelTime = 10l,
            scoreTime = 10l,
            tasksQueuedTime = 10l,
            totalJobTime = 10l,
            pipelineTimings = pipelineTimings
          )
        ),
        description = Some("desc"),
        cvModelPredictOptions = Some(
          CVModelPredictOptions(
            loi = Some(Seq(LabelOfInterest("label", 9.0))),
            defaultVisualThreshold = Some(9.0f),
            iouThreshold = Some(9.0f)
          )
        )
      )

      val document = dao.entityToDocument(prediction)
      val restoredPrediction = dao.documentToEntity(document)

      restoredPrediction shouldBe Success(prediction)
    }
  }

}
