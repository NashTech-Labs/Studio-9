package cortex.jobmaster.jobs.job.copier

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.transform.copier.CopierModule
import cortex.task.transform.copier.CopierParams._

import scala.concurrent.{ ExecutionContext, Future }

class CopierJob(
    scheduler:       TaskScheduler,
    copierModule:    CopierModule,
    copierJobConfig: CopierJobConfig
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  def copy(
    jobId:            String,
    copierTaskParams: CopierTaskParams,
    customTaskPath:   Option[String]   = None
  ): Future[CopierTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/copier")
    val taskId = genTaskId(jobId)
    val task = copierModule.transformTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = copierTaskParams,
      cpus     = copierJobConfig.cpus,
      memory   = copierJobConfig.taskMemoryLimit
    )
    scheduler.submitTask(task)
  }
}
