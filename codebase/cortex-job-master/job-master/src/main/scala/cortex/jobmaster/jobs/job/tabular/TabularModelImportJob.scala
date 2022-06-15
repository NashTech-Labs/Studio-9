package cortex.jobmaster.jobs.job.tabular

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.tabular_data.TabularModelImportModule
import cortex.task.tabular_data.TabularModelImportParams.{ TabularModelImportTaskParams, TabularModelImportTaskResult }

import scala.concurrent.Future

class TabularModelImportJob(
    scheduler: TaskScheduler,
    module:    TabularModelImportModule,
    config:    TabularModelImportJobConfig
) extends TaskIdGenerator {

  def importModel(
    jobId:          String,
    params:         TabularModelImportTaskParams,
    customTaskPath: Option[String]               = None
  ): Future[TabularModelImportTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/tabular_model_import")
    val taskId = genTaskId(jobId)
    val task = module.transformTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = config.cpus,
      memory   = config.taskMemoryLimit
    )
    scheduler.submitTask(task)
  }
}
