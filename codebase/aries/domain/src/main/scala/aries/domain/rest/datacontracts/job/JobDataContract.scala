package aries.domain.rest.datacontracts.job

import java.util.{ Date, UUID }

import aries.domain.rest.HttpContract
import aries.domain.service.job.JobStatus

import scala.concurrent.duration.FiniteDuration

object JobDataContract {

  case class CreateRequest(
    id:          UUID,
    owner:       UUID,
    jobType:     String,
    status:      JobStatus,
    inputPath:   String,
    submittedAt: Date,
    startedAt:   Option[Date]   = None,
    completedAt: Option[Date]   = None,
    outputPath:  Option[String] = None
  ) extends HttpContract

  case class TaskTimeInfo(taskName: String, timeInfo: TimeInfo) extends HttpContract

  case class TimeInfo(
    submittedAt: Date,
    startedAt:   Option[Date],
    completedAt: Option[Date]
  ) extends HttpContract

  case class UpdateTimeInfo(
    submittedAt: Option[Date] = None,
    startedAt:   Option[Date] = None,
    completedAt: Option[Date] = None
  ) extends HttpContract

  case class UpdateRequest(
    status:             Option[JobStatus]                  = None,
    timeInfo:           Option[UpdateTimeInfo]             = None,
    tasksTimeInfo:      Option[Seq[TaskTimeInfo]]          = None,
    tasksQueuedTime:    Option[FiniteDuration]             = None,
    outputPath:         Option[String]                     = None,
    cortexErrorDetails: Option[CortexErrorDetailsContract] = None
  ) extends HttpContract

  case class SearchRequest(
    owner:   Option[UUID]      = None,
    jobType: Option[String]    = None,
    status:  Option[JobStatus] = None
  ) extends HttpContract

  case class Response(
    id:                 UUID,
    owner:              UUID,
    jobType:            String,
    status:             JobStatus,
    inputPath:          String,
    timeInfo:           TimeInfo,
    tasksTimeInfo:      Seq[TaskTimeInfo],
    tasksQueuedTime:    Option[FiniteDuration],
    outputPath:         Option[String]                     = None,
    cortexErrorDetails: Option[CortexErrorDetailsContract] = None
  ) extends HttpContract

  case class CortexErrorDetailsContract(
    errorCode:     String,
    errorMessages: String,
    errorDetails:  Map[String, String]
  )

}
