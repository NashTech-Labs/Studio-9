package cortex.jobmaster.jobs.job.pipeline_runner

import cortex.TaskResult.BinaryResult
import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.pipeline_runner.PipelineRunnerModule
import cortex.task.pipeline_runner.PipelineRunnerParams.PipelineRunnerTaskParams

import scala.concurrent.{ ExecutionContext, Future }

class PipelineRunnerJob(
    scheduler: TaskScheduler,
    module:    PipelineRunnerModule,
    config:    PipelineRunnerJobConfig
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  def runPipeline(
    jobId:          String,
    params:         PipelineRunnerTaskParams,
    customTaskPath: Option[String]           = None
  ): Future[BinaryResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/run_pipeline")
    val taskId = genTaskId(jobId)
    val task = module.createTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = config.cpus,
      memory   = config.taskMemoryLimit,
      gpus     = config.gpus
    )
    scheduler.submitTask(task)
  }
}
