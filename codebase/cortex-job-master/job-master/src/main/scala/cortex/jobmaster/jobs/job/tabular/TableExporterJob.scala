package cortex.jobmaster.jobs.job.tabular

import cortex.TaskResult
import cortex.task.tabular_data.Table
import cortex.task.transform.common.{ CSVParams, Column, TableFileType }

import scala.concurrent.Future

trait TableExporterJob {

  def exportToTable(
    jobId:     String,
    table:     Table,
    srcPath:   String,
    fileType:  TableFileType,
    columns:   Seq[Column],
    csvParams: Option[CSVParams] = None
  ): Future[TaskResult.Empty]
}
