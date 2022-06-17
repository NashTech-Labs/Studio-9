package baile.services.tabular.prediction

import java.time.Instant
import java.util.UUID

import akka.actor.Props
import akka.pattern.ask
import baile.ExtendedBaseSpec
import baile.dao.tabular.prediction.TabularPredictionDao
import baile.daocommons.WithId
import baile.domain.job.CortexJobStatus
import baile.domain.table.TableStatus
import baile.domain.tabular.prediction.{ ColumnMapping, TabularPrediction, TabularPredictionStatus }
import baile.domain.usermanagement.User
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.process.JobResultHandler.HandleJobResult
import baile.services.table.TableService
import baile.services.table.util.TableRandomGenerator
import baile.services.tabular.model.TabularModelCommonService
import baile.services.usermanagement.util.TestData.SampleUser
import cortex.api.job.table.{ DataSource, Table, TableMeta }
import cortex.api.job.tabular._
import play.api.libs.json.Json

class TabularPredictionResultHandlerSpec extends ExtendedBaseSpec {

  trait Setup {

    implicit val user: User = SampleUser

    val tabularModelCommonService = mock[TabularModelCommonService]
    val tableService = mock[TableService]
    val cortexJobService = mock[CortexJobService]
    val jobMetaService = mock[JobMetaService]
    val dao = mock[TabularPredictionDao]

    val handler = system.actorOf(Props(new TabularPredictionResultHandler(
      tabularModelCommonService = tabularModelCommonService,
      cortexJobService = cortexJobService,
      jobMetaService = jobMetaService,
      tableService = tableService,
      dao = dao,
      logger = logger
    )))

    val jobId = UUID.randomUUID()
    val outputPath = "path"
    val table = TableRandomGenerator.randomTable()
    val dateTime = Instant.now
    val prediction = WithId(
      TabularPrediction(
        ownerId = user.id,
        name = "predict-name",
        created = dateTime,
        updated = dateTime,
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
        description = Some("description")
      ),
      "id"
    )
    val meta = TabularPredictionResultHandler.Meta(
      predictionId = prediction.id,
      userId = user.id
    )
  }

  "TabularPredictionResultHandler" should {

    "handle HandleJobResult message and update prediction" in new Setup {
      val outputTable = Table(Some(TableMeta(schema = "main", name = "abalone_holdout_prediction")))
      val outputSource = DataSource(table = Some(outputTable))
      val result = PredictionResult(output = Some(outputSource))

      tableService.updateStatus(prediction.entity.outputTableId, TableStatus.Active) shouldReturn future(())
      tableService.calculateColumnStatistics(*, *, *) shouldReturn future(())
      cortexJobService.getJobOutputPath(jobId) shouldReturn future(outputPath)
      jobMetaService.readRawMeta(jobId, outputPath) shouldReturn future(result.toByteArray)
      dao.get(prediction.id)(*) shouldReturn future(Some(prediction))
      dao.update(prediction.id, *)(*) shouldReturn future(Some(prediction))

      (handler ? HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        Json.toJsObject(meta)
      )).futureValue
    }

  }

}
