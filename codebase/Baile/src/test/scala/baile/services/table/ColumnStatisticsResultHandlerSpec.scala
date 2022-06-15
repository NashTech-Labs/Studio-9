package baile.services.table

import java.util.UUID

import akka.actor.Props
import akka.pattern.ask
import baile.BaseSpec
import baile.dao.table.TableDao
import baile.daocommons.WithId
import baile.domain.job.CortexJobStatus
import baile.domain.table.{ Table, TableStatisticsStatus }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.process.JobResultHandler.HandleJobResult
import baile.services.table.util.TestData.TableEntity
import cortex.api.job.table.TabularColumnStatisticsResponse
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext

class ColumnStatisticsResultHandlerSpec extends BaseSpec {

  private val tableDao = mock[TableDao]
  private val tableService = mock[TableService]
  private val cortexJobService = mock[CortexJobService]
  private val jobMetaService = mock[JobMetaService]
  private val handler = system.actorOf(Props(
    new ColumnStatisticsResultHandler(
      tableDao,
      tableService,
      cortexJobService,
      jobMetaService
    )
  ))
  private val jobId: UUID = UUID.randomUUID()
  private val userId: UUID = UUID.randomUUID()
  private val tableEntity = TableEntity.copy(
    columns = Seq(),
    tableStatisticsStatus = TableStatisticsStatus.Done
  )
  private val table = WithId(tableEntity, "id")
  val tabularColumnStatisticsResponse = TabularColumnStatisticsResponse(
    columnStatistics = Seq()
  )

  "ColumnStatisticsResultHandler#handle" should {

    "handle HandleJobResult message for column statistics result handler" in {

      when(cortexJobService.getJobOutputPath(eqTo(jobId))).thenReturn(future(randomPath()))
      when(jobMetaService.readRawMeta(
        eqTo(jobId),
        any[String]
      )).thenReturn(future(tabularColumnStatisticsResponse.toByteArray))
      when(tableService.loadTableMandatory(any[String])).thenReturn(future(table))
      when(tableDao.update(any[String], any[Table => Table].apply)(any[ExecutionContext]))
        .thenReturn(future(Some(table)))
      when(tableDao.get(any[String])(any[ExecutionContext])).thenReturn(future(Some(table)))

      (handler ? HandleJobResult(
        jobId = jobId,
        lastStatus = CortexJobStatus.Completed,
        rawMeta = Json.toJsObject(ColumnStatisticsResultHandler.Meta(
          tableId = table.id,
          userId = userId
        ))
      )).futureValue
    }

  }

}
