package cortex.jobmaster.jobs.job.splitter

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.transform.splitter.SplitterModule
import cortex.task.transform.splitter.SplitterParams._

import scala.concurrent.{ ExecutionContext, Future }

class SplitterJob(
    scheduler:         TaskScheduler,
    splitterModule:    SplitterModule,
    splitterJobConfig: SplitterJobConfig
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  def splitInput(
    jobId:              String,
    splitterTaskParams: SplitterTaskParams,
    customTaskPath:     Option[String]     = None
  ): Future[SplitterTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/splitter")
    val taskId = genTaskId(jobId)
    val task = splitterModule.transformTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = splitterTaskParams,
      cpus     = splitterJobConfig.cpus,
      memory   = splitterJobConfig.taskMemoryLimit
    )
    scheduler.submitTask(task)
  }
}
