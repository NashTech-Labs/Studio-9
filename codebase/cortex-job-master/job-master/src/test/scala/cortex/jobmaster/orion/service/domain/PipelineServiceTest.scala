package cortex.jobmaster.orion.service.domain

import java.io.File
import java.util.UUID

import cortex.api.job.pipeline.{ PipelineRunRequest, PipelineRunResponse, PipelineStepRequest, PipelineValue }
import cortex.api.job.project.`package`.ProjectPackageRequest
import cortex.api.job.common.ClassReference
import cortex.api.job.{ JobRequest, JobType }
import cortex.jobmaster.jobs.job.TestUtils
import cortex.jobmaster.modules.SettingsModule
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.{ FlatSpec, Matchers }

import scala.concurrent.ExecutionContext.Implicits.global

class PipelineServiceTest extends FlatSpec
  with Matchers
  with FutureTestUtils
  with WithS3AndLocalScheduler
  with SettingsModule {

  lazy val s3AccessParams = S3AccessParams(
    bucket      = baseBucket,
    accessKey   = accessKey,
    secretKey   = secretKey,
    region      = "",
    endpointUrl = Some(fakeS3Endpoint)
  )

  lazy val pipelineService = PipelineService(taskScheduler, s3AccessParams, fakeS3Client, this)
  lazy val projectPackagerService = ProjectPackagerService(taskScheduler, s3AccessParams, this)

  val baseProjectPath = "cortex-job-master/python_project"

  var packageLocation: String = _

  override def beforeAll(): Unit = {
    super.beforeAll()

    //upload package files to fake s3
    new File("../test_data/python_project/football").listFiles().filter(_.isFile).toList.foreach { f =>
      TestUtils.copyToS3(fakeS3Client, baseBucket, s"$baseProjectPath/football/${f.getName}", f.getAbsolutePath)
    }

    val projectPackageRequest = ProjectPackageRequest(
      projectFilesPath = baseProjectPath,
      name             = "test-project",
      version          = "0.1.0",
      targetPrefix     = "cortex-job-master/output_path"
    )
    val (result, _) = projectPackagerService.pack(jobId, projectPackageRequest).await()
    packageLocation = result.packageLocation
  }

  "PipelineService" should "run pipeline and get result" in {
    val request = PipelineRunRequest(
      Seq(
        PipelineStepRequest(
          "1",
          Some(ClassReference(
            packageLocation = Some(packageLocation),
            moduleName      = "football.get_random_number",
            className       = "GetRandomNumber"
          ))
        )
      )
    )
    val jobRequest = JobRequest(
      JobType.Pipeline,
      request.toByteString
    )

    val (result: PipelineRunResponse, _) = pipelineService.handlePartial((jobId, jobRequest)).await()
    result.pipelineStepsResponse.length shouldBe 1
    result.pipelineStepsResponse.head.response.pipelineStepGeneralResponse shouldBe defined
    val stepResponse = result.pipelineStepsResponse.head.response.pipelineStepGeneralResponse.get
    stepResponse.outputValues shouldBe Map(0 -> PipelineValue(PipelineValue.Value.IntValue(4)))
  }

  private def jobId = UUID.randomUUID().toString

}
