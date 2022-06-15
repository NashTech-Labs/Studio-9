package baile.services.tabular.model

import java.util.UUID

import akka.actor.Props
import akka.pattern.ask
import baile.ExtendedBaseSpec
import baile.dao.tabular.model.TabularModelDao
import baile.domain.job.CortexJobStatus
import baile.domain.tabular.model.TabularModelStatus
import baile.services.common.MLEntityExportImportService
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.process.JobResultHandler.{ HandleException, HandleJobResult }
import cortex.api.job.common.ModelReference
import cortex.api.job.tabular.TabularModelImportResult
import play.api.libs.json.Json

import scala.util.Success

class TabularModelImportResultHandlerSpec extends ExtendedBaseSpec {

  trait Setup {

    val cortexJobService = mock[CortexJobService]
    val importService = mock[MLEntityExportImportService]
    val tabularModelCommonService = mock[TabularModelCommonService]
    val jobMetaService = mock[JobMetaService]
    val modelDao = mock[TabularModelDao]

    val handler = system.actorOf(Props(new TabularModelImportResultHandler(
      cortexJobService = cortexJobService,
      importService = importService,
      tabularModelCommonService = tabularModelCommonService,
      jobMetaService = jobMetaService,
      modelDao = modelDao
    )))

    val jobId = UUID.randomUUID()
    val filePath = "file/path"
    val outputPath = "path"

    val model = TabularModelRandomGenerator.randomModel(status = TabularModelStatus.Saving)

  }

  "TabularModelImportResultHandler" should {

    "handle HandleJobResult message and update model" in new Setup {

      val result = TabularModelImportResult(
        tabularModelReference = Some(ModelReference(
          id = "id",
          filePath = "filePath"
        ))
      )

      importService.deleteImportedEntityFile(filePath) shouldReturn future(())
      tabularModelCommonService.loadModelMandatory(model.id) shouldReturn future(model)
      tabularModelCommonService.assertModelStatus(model, TabularModelStatus.Saving) shouldReturn Success(())
      cortexJobService.getJobOutputPath(jobId) shouldReturn future(outputPath)
      jobMetaService.readRawMeta(jobId, outputPath) shouldReturn future(result.toByteArray)
      modelDao.update(model.id, *)(*) shouldReturn future(Some(model))

      (handler ? HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        Json.toJsObject(TabularModelImportResultHandler.Meta(
          modelId = model.id,
          importedFilePath = filePath
        ))
      )).futureValue
    }

    "handle HandleException message and fail model" in new Setup {
      importService.deleteImportedEntityFile(filePath) shouldReturn future(())
      tabularModelCommonService.updateModelStatus(model.id, TabularModelStatus.Error) shouldReturn future(model)

      (handler ? HandleException(
        Json.toJsObject(TabularModelImportResultHandler.Meta(
          modelId = model.id,
          importedFilePath = filePath
        ))
      )).futureValue
    }

  }

}
