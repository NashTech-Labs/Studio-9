package cortex.redshift

import java.util.UUID

import cortex.E2EConfig
import cortex.io.S3Client
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.transform.importer.redshift.RedshiftImporterModule
import cortex.task.transform.importer.redshift.RedshiftImporterParams.RedshiftImporterTaskParams
import cortex.testkit._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._


class E2ERedshiftImporterTest extends FlatSpec with FutureTestUtils with WithS3AndLocalScheduler with E2EConfig {

  "RedshiftImporter" should "import table from Redshift DB into s3 as csv" in {
    val outputS3Path = s"s3://$s3Bucket/cortex-job-master/e2e/table_import/output.csv"
    val s3AccessParams = S3AccessParams(
      bucket = s3Bucket,
      accessKey = s3AccessKey,
      secretKey = s3SecretKey,
      region = s3Region
    )

    val params = RedshiftImporterTaskParams(
      redshiftAccessParams = redshiftAccessParams,
      table = redshiftTestTable,
      s3AccessParams = s3AccessParams,
      outputS3Path = outputS3Path
    )
    val redshiftExporterModule = new RedshiftImporterModule(s3Bucket, s3TestPath)
    val taskId = UUID.randomUUID().toString
    val task = redshiftExporterModule.transformTask(taskId, taskId, taskId, params, 1.0, 64.0)
    val result = taskScheduler.submitTask(task).await()

    val s3Client = new S3Client(s3AccessKey, s3SecretKey, s3Region)
    val tableData = new String(s3Client.get(s3Bucket, outputS3Path)).split("\n")

    val header = tableData.head
    header shouldBe "\"a\",\"b\",\"c\""

    val data = tableData.tail.toList
    data.size shouldBe 4
    data should contain("\"1\",\"2\",\"qew\"")
    data should contain("\"1\",\"2\",\"asd\"")
    data should contain("\"1\",\"243\",\"qew\"")
    data should contain("\"1\",\"2\",\"\"")
  }
}
