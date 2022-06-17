package cortex.jobmaster.jobs.job.tabular

import cortex.task.tabular_data.{ Table, TableImportResult }
import cortex.task.transform.common.CSVParams

import scala.concurrent.Future

trait TableImporterJob {
  def importFromTable(
    jobId:     String,
    table:     Table,
    destPath:  String,
    csvParams: Option[CSVParams] = None
  ): Future[TableImportResult]

}
