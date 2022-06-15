package cortex.task.transform.importer.redshift

import cortex.JsonSupport.SnakeJson
import cortex.TaskParams
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.TabularAccessParams.RedshiftAccessParams
import cortex.task.tabular_data.Table
import play.api.libs.json.OWrites

object RedshiftImporterParams {

  case class RedshiftImporterTaskParams(
      redshiftAccessParams: RedshiftAccessParams,
      table:                Table,
      s3AccessParams:       S3AccessParams,
      outputS3Path:         String
  ) extends TaskParams

  implicit val redshiftImporterTaskParamsWrites: OWrites[RedshiftImporterTaskParams] =
    SnakeJson.writes[RedshiftImporterTaskParams]

}
