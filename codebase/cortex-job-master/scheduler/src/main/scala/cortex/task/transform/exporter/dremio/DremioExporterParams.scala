package cortex.task.transform.exporter.dremio

import cortex.JsonSupport.SnakeJson
import cortex.TaskParams
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.TabularAccessParams.DremioAccessParams
import cortex.task.tabular_data.Table
import cortex.task.transform.common.{ CSVParams, Column, TableFileType }
import play.api.libs.json.OWrites

object DremioExporterParams {

  case class DremioExporterTaskParams(
      dremioAccessParams: DremioAccessParams,
      table:              Table,
      s3AccessParams:     S3AccessParams,
      s3SrcPath:          String,
      fileType:           TableFileType,
      columns:            Seq[Column],
      chunksize:          Int,
      csvParams:          Option[CSVParams]  = None
  ) extends TaskParams

  implicit val DremioExporterTaskParamsWrites: OWrites[DremioExporterTaskParams] =
    SnakeJson.writes[DremioExporterTaskParams]

}
