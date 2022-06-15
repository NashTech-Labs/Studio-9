package cortex.jobmaster.jobs.job.columns_statistics

import cortex.CortexException
import cortex.api.job.table.ColumnStatistics.Statistics.{
  CategoricalStatistics => ColumnCategoricalStatistics,
  NumericalStatistics => ColumnNumericalStatistics
}
import cortex.api.job.table.{ ColumnStatistics => CortexColumnStatistics, _ }
import cortex.common.Logging
import cortex.common.future.FutureExtensions._
import cortex.common.logging.JMLoggerFactory
import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.orion.service.domain.TableConverters
import cortex.scheduler.TaskScheduler
import cortex.task.TabularAccessParams
import cortex.task.column.ColumnVariableType
import cortex.task.column_statistics.ColumnStatisticsModule
import cortex.task.column_statistics.ColumnStatisticsParams.{
  ColumnInfo,
  ColumnStatisticsTaskParams,
  ColumnStatisticsTaskResult
}
import cortex.task.tabular_data.Table
import cortex.task.task_creators.GenericTask

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class ColumnStatisticsJob(
    scheduler:                 TaskScheduler,
    module:                    ColumnStatisticsModule,
    tabularAccessParams:       TabularAccessParams,
    columnStatisticsJobConfig: ColumnStatisticsJobConfig
)(implicit val context: ExecutionContext, val loggerFactory: JMLoggerFactory) extends TaskIdGenerator with Logging {

  def calculateColumnStatistics(
    jobId:  String,
    params: TabularColumnStatisticsRequest
  ): Future[(TabularColumnStatisticsResponse, JobTimeInfo)] =
    for {
      task <- createTask(jobId, params).toFuture
      result <- scheduler.submitTask(task)
      columnStatisticsResult = transformResult(params, result)
    } yield (columnStatisticsResult, JobTimeInfo(Seq(result.taskTimeInfo)))

  private def transformResult(
    request: TabularColumnStatisticsRequest,
    result:  ColumnStatisticsTaskResult
  ): TabularColumnStatisticsResponse = {
    val columnStatistics = result.columns.map { col =>
      val columnInfo = request.columns.find(_.name == col.name)
        .getOrElse(throw new CortexException(s"Unexpectedly not found column: ${col.name}"))
      CortexColumnStatistics(
        col.name,
        statistics = columnInfo.variableType match {
          case VariableType.CATEGORICAL =>
            val stats = col.categoricalStatistics.get
            ColumnCategoricalStatistics(
              CategoricalStatistics(
                uniqueValuesCount = stats.uniqueValuesCount,
                histogram         = Some(CategoricalHistogram(
                  stats.histogram.map { row =>
                    CategoricalHistogramRow(
                      value = row.value.getOrElse(""),
                      count = row.count
                    )
                  }
                ))
              )
            )
          case VariableType.CONTINUOUS =>
            val stats = col.numericalStatistics.get
            ColumnNumericalStatistics(
              NumericalStatistics(
                min           = stats.min,
                max           = stats.max,
                avg           = stats.avg,
                std           = stats.std,
                stdPopulation = stats.stdPopulation,
                mean          = stats.mean,
                histogram     = Some(NumericalHistogram(
                  stats.histogram.map { row =>
                    NumericalHistogramRow(
                      min   = row.min,
                      max   = row.max,
                      count = row.count
                    )
                  }
                ))
              )
            )
          case VariableType.Unrecognized(variableType) =>
            throw new RuntimeException(s"Invalid variable type $variableType")
        }
      )
    }

    TabularColumnStatisticsResponse(
      columnStatistics = columnStatistics,
      rowCount         = result.rowsCount
    )
  }

  private def createTask(
    jobId:                   String,
    columnStatisticsRequest: TabularColumnStatisticsRequest
  ): Try[GenericTask[ColumnStatisticsTaskResult, ColumnStatisticsTaskParams]] = Try {
    val table = TableConverters.apiDataSourceToTable(columnStatisticsRequest.dataSource.get)

    val taskParams = ColumnStatisticsTaskParams(
      accessParams    = tabularAccessParams,
      table           = Table(
        schema = table.schema,
        name   = table.name
      ),
      histogramLength = columnStatisticsRequest.histogramLength,
      columns         = columnStatisticsRequest.columns map { column =>
        ColumnInfo(
          column.name,
          TableConverters.apiDataTypeToDomain(column.datatype),
          TableConverters.apiVariableTypeToDomain(column.variableType)
        )
      }
    )

    val task = module.transformTask(
      id       = genTaskId(jobId),
      jobId    = jobId,
      taskPath = s"$jobId/calculate_statistics",
      params   = taskParams,
      cpus     = columnStatisticsJobConfig.cpus,
      memory   = columnStatisticsJobConfig.taskMemoryLimit
    )
    task.setAttempts(2)
    task
  }
}
