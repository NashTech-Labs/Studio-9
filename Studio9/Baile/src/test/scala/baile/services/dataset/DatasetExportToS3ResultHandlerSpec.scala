package baile.services.dataset

import java.time.Instant
import java.util.UUID

import akka.actor.{ ActorRef, Props }
import baile.dao.dataset.DatasetDao
import baile.domain.job.CortexJobStatus
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.process.JobResultHandler.HandleJobResult
import play.api.libs.json.Json
import baile.BaseSpec
import baile.daocommons.WithId
import baile.domain.dataset.{ Dataset, DatasetStatus }
import cortex.api.job.common.File
import cortex.api.job.dataset.{ S3DatasetExportResponse, UploadedDatasetFile }
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }

import scala.concurrent.ExecutionContext

class DatasetExportToS3ResultHandlerSpec extends BaseSpec with BeforeAndAfterEach {

  private val cortexJobService: CortexJobService = mock[CortexJobService]
  private val jobMetaService: JobMetaService = mock[JobMetaService]
  private val dao: DatasetDao = mock[DatasetDao]

  private val jobId: UUID = UUID.randomUUID()
  private val datasetId: String = randomString()

  private val handler: ActorRef = system.actorOf(Props(
    new DatasetExportToS3ResultHandler(
      cortexJobService,
      jobMetaService,
      dao,
      logger
    )
  ))

  private val dataset = WithId(Dataset(
    ownerId = UUID.randomUUID(),
    name = randomString(),
    status = DatasetStatus.Importing,
    created = Instant.now,
    updated = Instant.now,
    description = None,
    basePath = randomString()
  ), jobId.toString)

  when(cortexJobService.getJobOutputPath(any[UUID])).thenReturn(future(new RuntimeException("miss")))
  when(cortexJobService.getJobOutputPath(eqTo(jobId))).thenReturn(future(randomPath()))
  when(jobMetaService.readRawMeta(any[UUID], any[String])).thenReturn(future(new RuntimeException("miss")))
  when(jobMetaService.readRawMeta(eqTo(jobId), any[String])).thenReturn(future(S3DatasetExportResponse(
    datasets = Seq(UploadedDatasetFile(
      file = Some(File(
        filePath = randomPath("png"),
        fileSize = randomInt(999),
        fileName = randomString()
      )),
      metadata = Map.empty
    )),
    failedFiles = Seq.empty
  ).toByteArray))

  when(dao.update(any[String], any[Dataset => Dataset].apply)(any[ExecutionContext]))
    .thenReturn(future(new RuntimeException("miss")))
  when(dao.update(eqTo(datasetId), any[Dataset => Dataset].apply)(any[ExecutionContext]))
    .thenReturn(future(Some(dataset)))

  "DatasetExportToS3ResultHandler#handle" should {

    "handle HandleJobResult message" in {

      handler ! HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(DatasetExportToS3ResultHandler.Meta(
          datasetId = datasetId
        ))
      )
      expectMsgType[Unit]
    }

  }

}
