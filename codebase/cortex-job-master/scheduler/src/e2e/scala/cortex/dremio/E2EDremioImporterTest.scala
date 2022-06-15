package cortex.dremio

import java.util.UUID

import cortex.E2EConfig
import cortex.io.S3Client
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.transform.importer.dremio.DremioImporterModule
import cortex.task.transform.importer.dremio.DremioImporterParams.DremioImporterTaskParams
import cortex.testkit._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.util.Random


class E2EDremioImporterTest extends FlatSpec with FutureTestUtils with WithS3AndLocalScheduler with E2EConfig {

  /* TODO: run dremio as a docker container inside a test, move it from `e2e` to `test` scope
   * There should be 2 API calls to dremio after container is started:
   * - create user
   * - connect S3 to dremio
   */
  "DremioImporter" should "import table from Dremio DB into s3 as csv" in {
    val csvFilename = Random.alphanumeric.filter(c => c.isLetter && c <= 'z').take(7).mkString.toLowerCase
    val csvFilePath = s"e2e/$csvFilename.csv"
    val s3AccessParams = S3AccessParams(
      s3Bucket,
      s3AccessKey,
      s3SecretKey,
      s3Region
    )
    val params = DremioImporterTaskParams(
      dremioAccessParams,
      dremioTestTable,
      s3AccessParams,
      csvFilePath
    )
    val dremioImporterModule = new DremioImporterModule
    val taskId = UUID.randomUUID().toString
    val task = dremioImporterModule.transformTask(taskId, taskId, taskId, params, 1.0, 64.0)
    taskScheduler.submitTask(task).await()

    val s3Client = new S3Client(s3AccessKey, s3SecretKey, s3Region)
    val tableData = new String(s3Client.get(s3Bucket, csvFilePath)).split("\n")

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
