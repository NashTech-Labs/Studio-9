package cortex.redshift

import java.util.UUID

import cortex.E2EConfig
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.column.ColumnDataType
import cortex.task.tabular_data.Table
import cortex.task.transform.common.Column
import cortex.task.transform.exporter.redshift.RedshiftExporterModule
import cortex.task.transform.exporter.redshift.RedshiftExporterParams._
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.util.Random


class E2ERedshiftExporterTest extends FlatSpec with FutureTestUtils with WithS3AndLocalScheduler with E2EConfig {

  //to run this test the environment should be configured properly. [see application.conf]
  "RedshiftExporter" should "export csv from S3 into Redshift DB" in {
    val redshiftExporterModule = new RedshiftExporterModule
    val tableName = Random.alphanumeric.filter(c => c.isLetter && c <= 'z').take(7).mkString.toLowerCase
    val tableSchema = Random.alphanumeric.filter(c => c.isLetter && c <= 'z').take(7).mkString.toLowerCase
    val table = Table(tableSchema, tableName)
    val tableColumns = Seq(
      Column("col1", ColumnDataType.INTEGER),
      Column("col2", ColumnDataType.STRING),
      Column("col3", ColumnDataType.DOUBLE)
    )
    val s3AccessParams = S3AccessParams(
      bucket = s3Bucket,
      accessKey = s3AccessKey,
      secretKey = s3SecretKey,
      region = s3Region
    )
    val redshiftExporterTaskParams = RedshiftExporterTaskParams(
      redshiftAccessParams = redshiftAccessParams,
      table = table,
      columns = tableColumns,
      s3AccessParams = s3AccessParams,
      s3DataPath = redshiftDataSample,
      dropTable = true
    )
    val taskId = UUID.randomUUID().toString
    val task = redshiftExporterModule.transformTask(taskId, taskId, taskId, redshiftExporterTaskParams, cpus = 1.0, memory = 64.0)
    val results = taskScheduler.submitTask(task).await()
  }
}
