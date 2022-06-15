package aries.service.job

import java.util.UUID

import akka.actor.Props
import akka.pattern.pipe
import aries.common.service.{ DateSupport, NamedActor, Service, UUIDSupport }
import aries.domain.service.job._
import aries.domain.service.{ CreateEntity, DeleteEntity, UpdateEntity }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object JobCommandService extends NamedActor {
  override val Name = "job-command-service"
  def props()(implicit ec: ExecutionContext): Props = {
    Props(new JobCommandService())
  }
}

class JobCommandService(implicit ec: ExecutionContext) extends Service
    with UUIDSupport
    with DateSupport {

  log.debug(s"JobCommandService is starting at {}", self.path)

  val repository = JobRepository(context.system)

  def receive: Receive = {

    case CreateEntity(createJob: CreateJobData) => {
      def create(): Future[JobEntity] = {
        val jobEntity = JobEntity(
          id         = createJob.id,
          owner      = createJob.owner,
          job_type   = createJob.job_type,
          status     = createJob.status,
          input_path = createJob.input_path,
          time_info  = TimeInfo(submitted_at = createJob.submitted_at)
        )

        repository.create(jobEntity)
      }

      log.info("[ JobId: {} ] - [Create Job] - Creating job in ElasticSearch.", createJob.id)

      val result = create()

      result onComplete {
        case Success(_) => log.info("[ JobId: {} ] - [Create Job] - Job creation succeeded.", createJob.id)
        case Failure(e) => log.error("[ JobId: {} ] - [Create Job] - Failed to create Job [{}] with error: {}", createJob.id, createJob, e)
      }

      result pipeTo sender
    }

    case UpdateEntity(id: UUID, updateJob: UpdateJobData) => {
      def update(jobEntity: JobEntity): Future[Option[JobEntity]] = {

        def updateTimeInfo(newTimeInfo: UpdateTimeInfo): TimeInfo =
          TimeInfo(
            submitted_at = newTimeInfo.submitted_at.getOrElse(jobEntity.time_info.submitted_at),
            started_at   = newTimeInfo.started_at.orElse(jobEntity.time_info.started_at),
            completed_at = newTimeInfo.completed_at.orElse(jobEntity.time_info.completed_at)
          )

        val updatedEntity =
          jobEntity.copy(
            status               = updateJob.status.getOrElse(jobEntity.status),
            time_info            = updateJob.time_info.fold(jobEntity.time_info)(updateTimeInfo),
            output_path          = updateJob.output_path.orElse(jobEntity.output_path),
            tasks_queued_time    = updateJob.tasks_queued_time.orElse(jobEntity.tasks_queued_time),
            tasks_time_info      = updateJob.tasks_time_info.getOrElse(jobEntity.tasks_time_info),
            cortex_error_details = updateJob.cortex_error_details
          )
        repository.update(id, updatedEntity)
      }

      log.info("[ JobId: {} ] - [Update Job] - Updating job in ElasticSearch.", id)

      val result =
        repository.retrieve(id).flatMap {
          case Some(job) => update(job)
          case None      => Future.successful(None)
        }

      result onComplete {
        case Success(_) => log.info("[ JobId: {} ] - [Update Job] - Job update succeeded.", id)
        case Failure(e) => log.error("[ JobId: {} ] - [Update Job] - Failed to update Job [{}] with error: {}", id, updateJob, e)
      }

      result pipeTo sender
    }

    case DeleteEntity(id) => {
      def cancel(jobEntity: JobEntity): Future[Option[JobEntity]] = {
        val updatedEntity = jobEntity.copy(status = JobStatus.Cancelled)
        repository.update(id, updatedEntity)
      }

      log.info("[ JobId: {} ] - [Delete Job] - Deleting job from ElasticSearch.", id)

      // TODO: refactor this using monad transformers
      val result: Future[Option[JobEntity]] =
        repository.retrieve(id).flatMap {
          case Some(job) => cancel(job)
          case None      => Future.successful(None)
        }

      result onComplete {
        case Success(_) => log.info("[ JobId: {} ] - [Delete Job] - Job deletion succeeded.", id)
        case Failure(e) => log.error("[ JobId: {} ] - [Delete Job] - Failed to delete Job with error: {}", id, e)
      }

      result pipeTo sender
    }
  }

}

