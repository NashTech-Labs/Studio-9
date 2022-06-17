package aries.domain.service.job

import java.util.{ Date, UUID }

import aries.domain.service.{ DomainObject, UUIDEntity }

import scala.concurrent.duration.FiniteDuration

case class CreateJobData(
  id:           UUID,
  submitted_at: Date,
  owner:        UUID,
  job_type:     String,
  status:       JobStatus,
  input_path:   String
) extends DomainObject

case class JobEntity(
  id:                   UUID,
  owner:                UUID,
  job_type:             String,
  status:               JobStatus,
  input_path:           String,
  time_info:            TimeInfo,
  output_path:          Option[String]             = None,
  tasks_queued_time:    Option[FiniteDuration]     = None,
  tasks_time_info:      Seq[TaskTimeInfo]          = Seq.empty,
  cortex_error_details: Option[CortexErrorDetails] = None
) extends UUIDEntity

case class UpdateJobData(
  status:               Option[JobStatus]          = None,
  time_info:            Option[UpdateTimeInfo]     = None,
  tasks_queued_time:    Option[FiniteDuration]     = None,
  tasks_time_info:      Option[Seq[TaskTimeInfo]]  = None,
  output_path:          Option[String]             = None,
  cortex_error_details: Option[CortexErrorDetails] = None
) extends DomainObject

case class JobSearchCriteria(
  owner:    Option[UUID]      = None,
  job_type: Option[String]    = None,
  status:   Option[JobStatus] = None
) extends DomainObject

case class UpdateTimeInfo(
  submitted_at: Option[Date] = None,
  started_at:   Option[Date] = None,
  completed_at: Option[Date] = None
) extends DomainObject

case class TaskTimeInfo(task_name: String, time_info: TimeInfo) extends DomainObject

case class TimeInfo(
  submitted_at: Date,
  started_at:   Option[Date] = None,
  completed_at: Option[Date] = None
) extends DomainObject

case class CortexErrorDetails(
  error_code:     String,
  error_messages: String,
  error_details:  Map[String, String]
)
