package cortex.dremio

import java.util.UUID

import cortex.E2EConfig
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.column.ColumnDataType
import cortex.task.tabular_data.Table
import cortex.task.transform.common.{ Column, TableFileType }
import cortex.task.transform.exporter.dremio.DremioExporterModule
import cortex.task.transform.exporter.dremio.DremioExporterParams._
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.util.Random


class E2EDremioExporterTest extends FlatSpec with FutureTestUtils with WithS3AndLocalScheduler with E2EConfig {

  /* TODO: run dremio as a docker container inside a test, move it from `e2e` to `test` scope
   * There should be 2 API calls to dremio after container is started:
   * - create user
   * - connect S3 to dremio
   */
  //to run this test the environment should be configured properly. [see application.conf]
  "DremioExporter" should "export csv from S3 into Dremio DB" in {
    val dremioExporterModule = new DremioExporterModule
    val tableSchema = Random.alphanumeric.filter(c => c.isLetter && c <= 'z').take(7).mkString.toLowerCase
    val tableName = Random.alphanumeric.filter(c => c.isLetter && c <= 'z').take(7).mkString.toLowerCase
    val table = Table(tableSchema, tableName)
    val tableColumns = Seq(
      Column("col1", ColumnDataType.INTEGER),
      Column("col2", ColumnDataType.STRING),
      Column("col3", ColumnDataType.DOUBLE)
    )
    val s3AccessParams = S3AccessParams(
      s3Bucket,
      s3AccessKey,
      s3SecretKey,
      s3Region
    )
    val dremioExporterTaskParams = DremioExporterTaskParams(
      dremioAccessParams = dremioAccessParams,
      table = table,
      s3AccessParams = s3AccessParams,
      s3SrcPath = dremioDataSample,
      fileType = TableFileType.CSV,
      columns = tableColumns,
      chunksize = dremioExportingChunksize
    )
    val taskId = UUID.randomUUID().toString
    val task = dremioExporterModule.transformTask(taskId, taskId, taskId, dremioExporterTaskParams, cpus = 1.0, memory = 64.0)

    taskScheduler.submitTask(task).await()
  }
}
