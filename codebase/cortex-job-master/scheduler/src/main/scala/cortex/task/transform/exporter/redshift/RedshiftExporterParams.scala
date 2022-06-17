package cortex.task.transform.exporter.redshift

import cortex.JsonSupport.SnakeJson
import cortex.TaskParams
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.TabularAccessParams.RedshiftAccessParams
import cortex.task.tabular_data.Table
import cortex.task.transform.common.Column
import play.api.libs.json.OWrites

object RedshiftExporterParams {

  case class RedshiftExporterTaskParams(
      redshiftAccessParams: RedshiftAccessParams,
      table:                Table,
      columns:              Seq[Column],
      s3AccessParams:       S3AccessParams,
      s3DataPath:           String,
      dropTable:            Boolean              = false //used only for testing purposes
  ) extends TaskParams

  implicit val redshiftExporterTaskParamsWrites: OWrites[RedshiftExporterTaskParams] =
    SnakeJson.writes[RedshiftExporterTaskParams]
}
