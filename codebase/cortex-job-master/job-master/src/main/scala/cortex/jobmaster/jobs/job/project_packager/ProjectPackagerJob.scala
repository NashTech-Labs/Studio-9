package cortex.jobmaster.jobs.job.project_packager

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.project_packager.ProjectPackagerModule
import cortex.task.project_packager.ProjectPackagerParams.{ ProjectPackagerTaskParams, ProjectPackagerTaskResult }

import scala.concurrent.{ ExecutionContext, Future }

class ProjectPackagerJob(
    scheduler:                TaskScheduler,
    projectPackagerModule:    ProjectPackagerModule,
    projectPackagerJobConfig: ProjectPackagerJobConfig,
    val outputS3AccessParams: S3AccessParams
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  def pack(
    jobId:                     String,
    projectPackagerTaskParams: ProjectPackagerTaskParams,
    customTaskPath:            Option[String]            = None
  ): Future[ProjectPackagerTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/project_packager")
    val taskId = genTaskId(jobId)
    val task = projectPackagerModule.transformTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = projectPackagerTaskParams,
      cpus     = projectPackagerJobConfig.cpus,
      memory   = projectPackagerJobConfig.taskMemoryLimit
    )
    scheduler.submitTask(task)
  }
}
