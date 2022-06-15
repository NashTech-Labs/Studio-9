package baile.services.table

import java.util.UUID

import baile.dao.table.TableDao
import baile.daocommons.WithId
import baile.domain.job.{ CortexJobStatus, CortexJobTerminalStatus }
import baile.domain.table.{ ColumnStatistics, _ }
import baile.services.cortex.job.{ CortexJobService, JobMetaService }
import baile.services.process.JobResultHandler
import baile.services.table.ColumnStatisticsResultHandler.Meta
import baile.utils.TryExtensions._
import cortex.api.job.table.ColumnStatistics.Statistics.{
  CategoricalStatistics => CortexCategoricalStatistics,
  NumericalStatistics => CortexNumericalStatistics
}
import cortex.api.job.table.{ TabularColumnStatisticsResponse, ColumnStatistics => CortexColumnStatistics }
import play.api.libs.json.{ Json, OFormat, Reads }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class ColumnStatisticsResultHandler(
  tableDao: TableDao,
  tableService: TableService,
  cortexJobService: CortexJobService,
  jobMetaService: JobMetaService
) extends JobResultHandler[Meta] {

  override protected val metaReads: Reads[Meta] = ColumnStatisticsResultHandler.ColumnStatisticsResultHandlerMetaFormat

  override protected def handleResult(
    jobId: UUID,
    lastStatus: CortexJobTerminalStatus,
    meta: Meta
  )(implicit ec: ExecutionContext): Future[Unit] = {

    lastStatus match {
      case CortexJobStatus.Completed =>
        for {
          outputPath <- cortexJobService.getJobOutputPath(jobId)
          rawJobResult <- jobMetaService.readRawMeta(jobId, outputPath)
          table <- tableService.loadTableMandatory(meta.tableId)
          columnStatisticsResult <- Try(TabularColumnStatisticsResponse.parseFrom(rawJobResult)).toFuture
          columns = buildColumns(table.entity, columnStatisticsResult.columnStatistics)
          _ <- tableDao.update(
            meta.tableId,
            _.copy(
              columns = columns,
              tableStatisticsStatus = TableStatisticsStatus.Done,
              size = Some(columnStatisticsResult.rowCount)
            )
          )
        } yield ()
      case CortexJobStatus.Cancelled | CortexJobStatus.Failed =>
        handleException(meta)
    }

  }

  override protected def handleException(meta: Meta): Future[Unit] = failTableStatistics(meta.tableId).map(_ => ())

  private def failTableStatistics(tableId: String): Future[Option[WithId[Table]]] =
    tableDao.update(tableId, _.copy(tableStatisticsStatus = TableStatisticsStatus.Error))

  private def buildColumns(table: Table, columnStatistics: Seq[CortexColumnStatistics]): Seq[Column] = {
    table.columns.map { column =>
      columnStatistics.find(_.columnName == column.name) match {
        case Some(columnStatistic) => column.copy(
          statistics = Some(cortexStatisticsToColumnStatistics(columnStatistic.statistics))
        )
        case None => column
      }
    }
  }

  private def cortexStatisticsToColumnStatistics(statistics: CortexColumnStatistics.Statistics): ColumnStatistics =
    statistics match {
      case categoricalStatistics: CortexCategoricalStatistics => CategoricalStatistics(
        uniqueValuesCount = categoricalStatistics.value.uniqueValuesCount,
        categoricalHistogram = CategoricalHistogram(
          categoricalStatistics.value.getHistogram.valueFrequencies.map(
            valueFrequencies => CategoricalHistogramRow(
              value = if (valueFrequencies.value.isEmpty) None else Some(valueFrequencies.value),
              count = valueFrequencies.count
            )
          )
        )
      )
      case numericalStatistics: CortexNumericalStatistics => NumericalStatistics(
        min = numericalStatistics.value.min,
        max = numericalStatistics.value.max,
        avg = numericalStatistics.value.avg,
        std = numericalStatistics.value.std,
        stdPopulation = numericalStatistics.value.stdPopulation,
        mean = numericalStatistics.value.mean,
        numericalHistogram = NumericalHistogram(
          numericalStatistics.value.getHistogram.valueRanges.map(
            valueRange => NumericalHistogramRow(
              min = valueRange.min,
              max = valueRange.max,
              count = valueRange.count
            )
          )
        )
      )
      case CortexColumnStatistics.Statistics.Empty => throw new RuntimeException("Statistics is empty")
    }

}

object ColumnStatisticsResultHandler {

  case class Meta(tableId: String, userId: UUID)

  implicit val ColumnStatisticsResultHandlerMetaFormat: OFormat[Meta] = Json.format[Meta]

}
