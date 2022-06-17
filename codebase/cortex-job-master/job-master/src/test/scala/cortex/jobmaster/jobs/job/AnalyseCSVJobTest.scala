package cortex.jobmaster.jobs.job

import java.io.File
import java.util.UUID

import cortex.api.job.table._
import cortex.jobmaster.jobs.job.analyse_csv.AnalyseCSVJob
import cortex.jobmaster.modules.SettingsModule
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.analyse_csv.AnalyseCSVModule
import cortex.task.column.ColumnDataType
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global

class AnalyseCSVJobTest extends FlatSpec
  with FutureTestUtils
  with WithS3AndLocalScheduler
  with SettingsModule {

  val analyseCSVModule = new AnalyseCSVModule
  val s3AccessParams = S3AccessParams(
    bucket      = baseBucket,
    accessKey   = accessKey,
    secretKey   = secretKey,
    region      = "",
    endpointUrl = Some(fakeS3Endpoint)
  )
  val baseTablesPath = "cortex-job-master/e2e/table_samples"

  override def beforeAll(): Unit = {
    super.beforeAll()

    //upload sample table into fake s3
    val testFile = new File("../test_data/abalone.csv")
    TestUtils.copyToS3(fakeS3Client, baseBucket, s"$baseTablesPath/${testFile.getName}", testFile.getAbsolutePath)
  }

  lazy val analyseCSVJob = new AnalyseCSVJob(
    scheduler           = taskScheduler,
    module              = analyseCSVModule,
    inputAccessParams   = s3AccessParams,
    analyseCSVJobConfig = analyseCSVConfig
  )

  "AnalyseCSVJob" should "analyse columns from csv" in {

    val params = TableUploadRequest(
      dataSource     = Some(DataSource(
        table = Some(Table(
          Some(TableMeta(
            schema = "public",
            name   = "abalone"
          ))
        ))
      )),
      sourceFilePath = s"$baseTablesPath/abalone.csv",
      delimeter      = ",",
      nullValue      = "\\N",
      fileType       = FileType.CSV
    )
    val (result, _) = analyseCSVJob.analyseCSV(UUID.randomUUID().toString, params).await()

    result.length shouldBe 9
    result.map(_.dataType) shouldBe Seq(
      ColumnDataType.BOOLEAN,
      ColumnDataType.STRING,
      ColumnDataType.DOUBLE,
      ColumnDataType.DOUBLE,
      ColumnDataType.DOUBLE,
      ColumnDataType.DOUBLE,
      ColumnDataType.DOUBLE,
      ColumnDataType.DOUBLE,
      ColumnDataType.DOUBLE
    )
  }

}
