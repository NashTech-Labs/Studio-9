package cortex.jobmaster.jobs.job.computer_vision

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.computer_vision.ModelImportModule
import cortex.task.computer_vision.ModelImportParams.{ ModelImportTaskParams, ModelImportTaskResult }

import scala.concurrent.{ ExecutionContext, Future }

class ModelImportJob(
    scheduler:            TaskScheduler,
    module:               ModelImportModule,
    modelImportJobConfig: ModelImportJobConfig
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  def importModel(
    jobId:          String,
    params:         ModelImportTaskParams,
    customTaskPath: Option[String]        = None
  ): Future[ModelImportTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/model_import")
    val taskId = genTaskId(jobId)
    val task = module.transformTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = modelImportJobConfig.cpus,
      memory   = modelImportJobConfig.taskMemoryLimit
    )
    scheduler.submitTask(task)
  }
}
