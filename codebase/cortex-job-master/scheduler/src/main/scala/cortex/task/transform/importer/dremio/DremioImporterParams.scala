package cortex.task.transform.importer.dremio

import cortex.JsonSupport.SnakeJson
import cortex.TaskParams
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.TabularAccessParams.DremioAccessParams
import cortex.task.tabular_data.Table
import cortex.task.transform.common.CSVParams
import play.api.libs.json.OWrites

object DremioImporterParams {

  case class DremioImporterTaskParams(
      dremioAccessParams: DremioAccessParams,
      table:              Table,
      s3AccessParams:     S3AccessParams,
      s3DestPath:         String,
      csvParams:          Option[CSVParams]  = None
  ) extends TaskParams

  implicit val DremioImporterTaskParamsWrites: OWrites[DremioImporterTaskParams] =
    SnakeJson.writes[DremioImporterTaskParams]
}
