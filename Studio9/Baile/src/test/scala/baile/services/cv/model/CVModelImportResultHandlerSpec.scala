package baile.services.cv.model

import java.util.UUID

import akka.actor.Props
import akka.pattern.ask
import baile.ExtendedBaseSpec
import baile.dao.cv.model.CVModelDao
import baile.domain.cv.model.CVModelStatus
import baile.domain.job.CortexJobStatus
import baile.services.common.MLEntityExportImportService
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.process.JobResultHandler.{ HandleException, HandleJobResult }
import cortex.api.job.common.ModelReference
import cortex.api.job.computervision.CVModelImportResult
import play.api.libs.json.Json

import scala.util.Success

class CVModelImportResultHandlerSpec extends ExtendedBaseSpec {

  trait Setup {

    val cortexJobService = mock[CortexJobService]
    val importService = mock[MLEntityExportImportService]
    val cvModelCommonSerivce = mock[CVModelCommonService]
    val jobMetaService = mock[JobMetaService]
    val modelDao = mock[CVModelDao]

    val handler = system.actorOf(Props(new CVModelImportResultHandler(
      cvModelCommonService = cvModelCommonSerivce,
      importService = importService,
      cortexJobService = cortexJobService,
      jobMetaService = jobMetaService,
      modelDao = modelDao
    )))

    val jobId = UUID.randomUUID()
    val filePath = "file/path"
    val outputPath = "path"

    val model = CVModelRandomGenerator.randomModel(status = CVModelStatus.Saving)

  }

  "CVModelImportResultHandler" should {

    "handle HandleJobResult message and update model" in new Setup {

      val result = CVModelImportResult(
        featureExtractorReference = Some(ModelReference(
          id = "id",
          filePath = "filePath"
        )),
        cvModelReference = Some(ModelReference(
          id = "id",
          filePath = "filePath"
        ))
      )

      importService.deleteImportedEntityFile(filePath) shouldReturn future(())
      cvModelCommonSerivce.loadModelMandatory(model.id) shouldReturn future(model)
      cvModelCommonSerivce.assertModelStatus(model, CVModelStatus.Saving) shouldReturn Success(())
      cortexJobService.getJobOutputPath(jobId) shouldReturn future(outputPath)
      jobMetaService.readRawMeta(jobId, outputPath) shouldReturn future(result.toByteArray)
      modelDao.update(model.id, *)(*) shouldReturn future(Some(model))

      (handler ? HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        Json.toJsObject(CVModelImportResultHandler.Meta(
          modelId = model.id,
          importedFilePath = filePath
        ))
      )).futureValue
    }

    "handle HandleException message and fail model" in new Setup {
      importService.deleteImportedEntityFile(filePath) shouldReturn future(())
      cvModelCommonSerivce.updateModelStatus(model.id, CVModelStatus.Error) shouldReturn future(model)

      (handler ? HandleException(
        Json.toJsObject(CVModelImportResultHandler.Meta(
          modelId = model.id,
          importedFilePath = filePath
        ))
      )).futureValue
    }

  }

}
