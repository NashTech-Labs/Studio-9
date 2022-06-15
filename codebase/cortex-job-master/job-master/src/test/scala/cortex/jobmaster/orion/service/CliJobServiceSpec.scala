package cortex.jobmaster.orion.service

import java.io.{ File, FileOutputStream }
import java.util.{ Date, UUID }

import com.google.protobuf.ByteString
import com.trueaccord.scalapb.GeneratedMessage
import cortex.TaskTimeInfo
import cortex.api.job.JobRequest
import cortex.api.job.JobType.S3ImagesImport
import cortex.api.job.computervision.CVModelTrainResult
import cortex.jobmaster.jobs.time.JobTimeInfo
import cortex.jobmaster.orion.service.domain.JobRequestHandler
import cortex.jobmaster.orion.service.io._
import cortex.scheduler.TaskScheduler
import cortex.testkit.{ BaseSpec, WithLogging }
import org.apache.commons.io.FileUtils
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{ Second, Span }
import org.scalatest.{ BeforeAndAfterAll, OneInstancePerTest }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Future, Promise }

class CliJobServiceSpec extends BaseSpec
  with OneInstancePerTest
  with ScalaFutures
  with BeforeAndAfterAll
  with MockFactory
  with WithLogging {

  val jobId: String = "80288972-b5ce-4b56-950d-47c9198761e5"
  val filePath = "target/test.txt"
  val marshaller = new PbByteArrayMarshaller[JobRequest]
  val jobRequest = JobRequest(S3ImagesImport, ByteString.EMPTY)
  val bytes: Array[Byte] = marshaller.marshall(jobRequest)

  override def beforeAll(): Unit = {
    super.beforeAll()
    val file = new File(filePath)
    file.createNewFile()
    val out = new FileOutputStream(file)
    out.write(bytes)
    out.close()
  }

  override def afterAll(): Unit = {
    FileUtils.deleteQuietly(new File(filePath))
    super.afterAll()
  }

  "CliJobServiceAdapter" should {
    "get job request from a file on start and serialize it properly" in {
      val isHandled = Promise[(JobRequest, JobTimeInfo)]()
      val expectedJobTimeInfo = JobTimeInfo(Seq(TaskTimeInfo("task_id", new Date(), Some(new Date()), Some(new Date()))))

      val jobService = new CliJobService(
        jobId             = jobId,
        filePath          = filePath,
        jobRequestHandler = mock[JobRequestHandler],
        taskScheduler     = mock[TaskScheduler]
      ) {
        override def startJob(jobId: UUID, jobRequest: JobRequest): Future[(GeneratedMessage, JobTimeInfo)] = {
          isHandled.success((jobRequest, expectedJobTimeInfo)).future
        }
      }

      jobService.start()

      whenReady(isHandled.future) {
        case (result, jobTimeInfo) =>
          result shouldBe jobRequest
          jobTimeInfo shouldBe expectedJobTimeInfo
      }
    }

    "on successful job completion stop TaskScheduler" in {
      val modelTrainResult = CVModelTrainResult()
      val expectedJobTimeInfo = JobTimeInfo(Seq(TaskTimeInfo("task_id", new Date(), Some(new Date()), Some(new Date()))))

      val jobRequestHandler = stub[JobRequestHandler]
      val taskScheduler = mock[TaskScheduler]

      (jobRequestHandler.handleJobRequest _).when((jobId, jobRequest)).returns(Future.successful((modelTrainResult, expectedJobTimeInfo)))
      (taskScheduler.stop _).expects()

      val jobService = new CliJobService(
        jobId             = jobId,
        filePath          = filePath,
        jobRequestHandler = jobRequestHandler,
        taskScheduler     = taskScheduler
      )

      val jobResult = jobService.start()

      val timeout = Timeout(Span(1, Second))
      whenReady(jobResult, timeout) {
        case (result, jobTimeInfo) =>
          result shouldBe modelTrainResult
          jobTimeInfo shouldBe expectedJobTimeInfo
      }
    }

    "on job failure stop TaskScheduler" in {
      val jobRequestHandler = stub[JobRequestHandler]
      val taskScheduler = mock[TaskScheduler]

      (jobRequestHandler.handleJobRequest _).when((jobId, jobRequest)).returns(Future.failed(new RuntimeException("BOOM!!!")))
      (taskScheduler.stop _).expects()

      val jobService = new CliJobService(
        jobId             = jobId,
        filePath          = filePath,
        jobRequestHandler = jobRequestHandler,
        taskScheduler     = taskScheduler
      )

      val jobResult = jobService.start()

      assert(jobResult.failed.futureValue.isInstanceOf[RuntimeException])
    }
  }
}
