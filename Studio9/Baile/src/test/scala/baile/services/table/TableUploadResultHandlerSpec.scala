package baile.services.table

import java.util.UUID

import akka.actor.Props
import akka.pattern.ask
import baile.BaseSpec
import baile.dao.table.TableDao
import baile.daocommons.WithId
import baile.domain.job.CortexJobStatus
import baile.domain.table.{ Column, Table }
import baile.services.common.FileUploadService
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.process.JobResultHandler.HandleJobResult
import baile.services.remotestorage.S3StorageService
import baile.services.table.util.TestData._
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

class TableUploadResultHandlerSpec extends BaseSpec {

  private val tableDao = mock[TableDao]
  private val cortexJobService = mock[CortexJobService]
  private val tableService = mock[TableService]
  private val jobMetaService = mock[JobMetaService]
  private val s3StorageService = mock[S3StorageService]
  private val fileUploadService = new FileUploadService(s3StorageService, "prefix")
  private val handler = system.actorOf(Props(
    new TableUploadResultHandler(
      tableDao,
      cortexJobService,
      fileUploadService,
      jobMetaService,
      tableService
    )
  ))
  private val jobId: UUID = UUID.randomUUID()
  private val userId: UUID = UUID.randomUUID()
  private val table = WithId(TableEntity, "id")
  private val tableUploadResponse = {
    import cortex.api.job.table.{ TableUploadResponse, Column, DataType, VariableType }

    TableUploadResponse(
      columns = Seq(Column(
        name = randomString(),
        displayName = randomString(),
        datatype = randomOf(DataType.values: _*),
        variableType = randomOf(VariableType.values: _*)
      ))
    )
  }
  private val s3Path: String = randomPath()

  "TableUploadResultHandler#handle" should {

    "handle HandleJobResult message for table upload" in {

      when(cortexJobService.getJobOutputPath(eqTo(jobId))).thenReturn(future(randomPath()))
      when(jobMetaService.readRawMeta(eqTo(jobId), any[String])).thenReturn(future(tableUploadResponse.toByteArray))
      when(tableDao.update(any[String], any[Table => Table].apply)(any[ExecutionContext]))
        .thenReturn(future(Some(table)))
      when(s3StorageService.delete(eqTo(s3Path))(any[ExecutionContext])).thenReturn(future(()))
      when(tableService.calculateColumnStatistics(
        any[String],
        any[Option[Seq[Column]]],
        any[UUID]
      )).thenReturn(future(()))

      (handler ? HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(TableUploadResultHandler.Meta(
          tableId = table.id,
          uploadedFilePath = s3Path,
          userId = userId
        ))
      )).futureValue
    }

  }

}
