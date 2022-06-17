package baile.services.cortex.job

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.domain.job.{ CortexJobProgress, CortexJobStatus }
import baile.services.cortex.datacontract.{
  CortexJobCreateRequest,
  CortexJobResponse,
  CortexJobStatusResponse,
  CortexTimeInfoResponse
}
import baile.services.cortex.job.SupportedCortexJobTypes._
import cortex.api.job.common.ClassReference
import cortex.api.job.JobRequest
import cortex.api.job.album.common.{ Image, Tag, TaggedImage }
import cortex.api.job.computervision._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._

import scala.concurrent.duration._

class CortexJobServiceSpec extends BaseSpec {

  private val jobMetaService = mock[JobMetaService]
  private val cortexService = mock[CortexService]
  private val ariesService = mock[AriesService]

  private val cortexJobService = new CortexJobService(jobMetaService, cortexService, ariesService)

  private val jobId = UUID.randomUUID
  private val userId = UUID.randomUUID
  private val inputPath = "input/path"
  private val outputPath = "output/path"

  val cortexTimeInfo: CortexTimeInfoResponse = CortexTimeInfoResponse(
    submittedAt = Instant.now(),
    completedAt = Some(Instant.now()),
    startedAt = Some(Instant.now())
  )

  val jobResponse = CortexJobResponse(
    id = jobId,
    owner = userId,
    jobType = "TRAIN",
    status = CortexJobStatus.Queued,
    inputPath = inputPath,
    outputPath = Some(outputPath),
    timeInfo = cortexTimeInfo,
    tasksQueuedTime = Some(10.minutes),
    tasksTimeInfo = Seq.empty
  )

  "CortexJobService#submitJob" should {
    val jobRequest = CVModelTrainRequest(
      featureExtractorId = Some("feid"),
      featureExtractorClassReference = Some(ClassReference(
        None,
        "ml_lib.feature_extractors.backbones",
        "StackedAutoEncoder"
      )),
      images = Seq(
        TaggedImage(Some(Image("image1")), Seq(Tag("label1"))),
        TaggedImage(Some(Image("image2")), Seq(Tag("label2")))
      ),
      filePathPrefix = randomString()
    )

    "successfully submit new job to cortex" in {
      when(jobMetaService.writeMeta(any[UUID], any[JobRequest])).thenReturn(future(inputPath))
      when(cortexService.createJob(any[CortexJobCreateRequest])).thenReturn(future(jobResponse))

      whenReady(cortexJobService.submitJob(jobRequest, userId))(_ shouldBe jobId)
    }

    val error = new RuntimeException("BOOM")

    "fail to submit new job to cortex (error on writing meta)" in {
      when(jobMetaService.writeMeta(any[UUID], any[JobRequest])).thenReturn(future(error))

      whenReady(cortexJobService.submitJob(jobRequest, userId).failed)(_ shouldBe error)
    }

    "fail to submit new job to cortex (error on creating job via cortex service)" in {
      when(jobMetaService.writeMeta(any[UUID], any[JobRequest])).thenReturn(future(inputPath))
      when(cortexService.createJob(any[CortexJobCreateRequest])).thenReturn(future(error))

      whenReady(cortexJobService.submitJob(jobRequest, userId).failed)(_ shouldBe error)
    }

  }

  "CortexJobService#getJobOutputPath" should {
    "return job output path" in {
      when(ariesService.getJob(jobId)).thenReturn(future(jobResponse))

      whenReady(cortexJobService.getJobOutputPath(jobId))(_ shouldBe outputPath)
    }

    "fail when output path is not set" in {
      when(ariesService.getJob(jobId)).thenReturn(future(jobResponse.copy(outputPath = None)))

      whenReady(cortexJobService.getJobOutputPath(jobId).failed)(_ shouldBe a [JobHasNoOutputPathException])
    }
  }

  "CortexJobService#getJobProgress" should {
    "return job progress" in {
      import scala.concurrent.duration._
      val jobStatusResponse = CortexJobStatusResponse(
        status = CortexJobStatus.Running,
        currentProgress = Some(0.3D),
        estimatedTimeRemaining = Some(20.seconds),
        cortexErrorDetails = None
      )
      when(cortexService.getJobStatus(jobId)).thenReturn(future(jobStatusResponse))

      whenReady(cortexJobService.getJobProgress(jobId))(_ shouldBe CortexJobProgress(
        jobId = jobId,
        status = jobStatusResponse.status,
        progress = jobStatusResponse.currentProgress,
        estimatedTimeRemaining = jobStatusResponse.estimatedTimeRemaining,
        cortexErrorDetails = None
      ))
    }
  }

  "CortexJobService#cancelJob" should {
    "cancel job in cortex" in {
      when(cortexService.cancelJob(jobId)).thenReturn(future(()))

      cortexJobService.cancelJob(jobId).futureValue
    }
  }

}
