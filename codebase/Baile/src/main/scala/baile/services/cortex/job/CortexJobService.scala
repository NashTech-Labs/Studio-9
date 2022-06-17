package baile.services.cortex.job

import java.util.UUID

import akka.event.LoggingAdapter
import baile.domain.job._
import baile.services.cortex.datacontract.{
  CortexJobCreateRequest,
  CortexJobStatusResponse,
  CortexTaskTimeInfoResponse,
  CortexTimeInfoResponse
}
import baile.services.cortex.job.SupportedCortexJobTypes.SupportedCortexJobType
import baile.utils.TryExtensions._
import com.trueaccord.scalapb.GeneratedMessage
import cortex.api.job.{ JobRequest, JobType => ProtobufJobType }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class CortexJobService(
  val jobMetaService: JobMetaService,
  val cortexService: CortexService,
  val ariesService: AriesService
)(implicit logger: LoggingAdapter, ec: ExecutionContext) {

  def submitJob[T <: GeneratedMessage: SupportedCortexJobType](
    request: T,
    userId: UUID
  ): Future[UUID] = {

    val jobId = UUID.randomUUID()
    JobLogging.info(jobId, "Generated Job Id", "Create Job")

    val protobufJobType = implicitly[SupportedCortexJobType[T]].protobufJobType
    val logString = implicitly[SupportedCortexJobType[T]].logString

    def buildJobRequest(jobType: ProtobufJobType): Try[JobRequest] = Try {
      JobRequest(`type` = jobType, payload = request.toByteString)
    }

    for {
      jobRequest <- buildJobRequest(protobufJobType).toFuture
      jobInputPath <- jobMetaService.writeMeta(jobId, jobRequest)
      cortexJobCreateRequest = CortexJobCreateRequest(
        id = jobId,
        owner = userId,
        jobType = logString,
        inputPath = jobInputPath
      )
      cortexJobResponse <- cortexService.createJob(cortexJobCreateRequest)
    } yield cortexJobResponse.id
  }

  def getJobOutputPath(id: UUID): Future[String] =
    ariesService.getJob(id).map(_.outputPath.getOrElse(throw JobHasNoOutputPathException(id)))

  def getJobProgress(id: UUID): Future[CortexJobProgress] =
    cortexService.getJobStatus(id).map {
      case CortexJobStatusResponse(status, currentProgress, estimatedTimeRemaining, cortexErrorDetails) =>
        CortexJobProgress(id, status, currentProgress, estimatedTimeRemaining, cortexErrorDetails)
    }

  def getJobTimeSummary(id: UUID): Future[CortexJobTimeSpentSummary] = {

    def assertJobStatus(cortexJobStatus: CortexJobStatus): Try[Unit] = Try {
      cortexJobStatus match {
        case _: CortexJobTerminalStatus => ()
        case nonterminalStatus => throw new RuntimeException(s"Got non terminal status $nonterminalStatus")
      }
    }

    def buildCortexTimeInfo(cortexTimeInfo: CortexTimeInfoResponse): Try[CortexTimeInfo] = {

      def error(fieldName: String) = throw new RuntimeException(s"Not found timestamp for field '$fieldName'")

      Try {
        CortexTimeInfo(
          submittedAt = cortexTimeInfo.submittedAt,
          startedAt = cortexTimeInfo.startedAt.getOrElse(error("startedAt")),
          completedAt = cortexTimeInfo.completedAt.getOrElse(error("completedAt"))
        )
      }
    }

    def buildTasksTimeInfo(tasksTimeInfo: Seq[CortexTaskTimeInfoResponse]): Try[Seq[CortexTaskTimeInfo]] =
      Try.sequence(tasksTimeInfo.map { taskTimeInfo =>
        buildCortexTimeInfo(taskTimeInfo.timeInfo).map { cortexTimeInfo =>
          CortexTaskTimeInfo(taskTimeInfo.taskName, cortexTimeInfo)
        }
      })

    for {
      jobResponse <- ariesService.getJob(id)
      _ <- assertJobStatus(jobResponse.status).toFuture
      jobTimeInfo <- buildCortexTimeInfo(jobResponse.timeInfo).toFuture
      tasksTimeInfo <- buildTasksTimeInfo(jobResponse.tasksTimeInfo).toFuture
      tasksQueuedTime <- Try(jobResponse.tasksQueuedTime.getOrElse(throw new RuntimeException(
        "Could not find tasksQueuedTime"
      ))).toFuture
    } yield CortexJobTimeSpentSummary(
      tasksQueuedTime = tasksQueuedTime.toSeconds,
      jobTimeInfo = jobTimeInfo,
      tasksTimeInfo = tasksTimeInfo
    )
  }

  def cancelJob(id: UUID): Future[Unit] =
    cortexService.cancelJob(id)

  private[services] def buildPipelineTimings(cortexPipelineTimings: Map[String, Long]): List[PipelineTiming] =
    cortexPipelineTimings.toList.map { case (description, time) =>
      PipelineTiming(description, time)
    }

}

object CortexJobService {

  sealed trait CortexJobServiceError
  object CortexJobServiceError {
    case class UnsupportedRequestType(request: GeneratedMessage) extends CortexJobServiceError
  }

}
