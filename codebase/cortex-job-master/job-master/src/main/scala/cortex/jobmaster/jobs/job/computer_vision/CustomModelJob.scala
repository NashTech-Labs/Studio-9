package cortex.jobmaster.jobs.job.computer_vision

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.computer_vision.CustomModelModule
import cortex.task.computer_vision.CustomModelParams._

import scala.concurrent.{ ExecutionContext, Future }

class CustomModelJob(
    scheduler:            TaskScheduler,
    module:               CustomModelModule,
    customModelJobConfig: CustomModelJobConfig
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  def score(
    jobId:          String,
    params:         ScoreTaskParams,
    customTaskPath: Option[String]  = None
  ): Future[ScoreTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/custom_model_score")
    val taskId = genTaskId(jobId)
    val task = module.scoreTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = customModelJobConfig.cpus,
      memory   = customModelJobConfig.taskMemoryLimit,
      gpus     = customModelJobConfig.gpus
    )
    scheduler.submitTask(task)
  }

  def predict(
    jobId:          String,
    params:         PredictTaskParams,
    customTaskPath: Option[String]    = None
  ): Future[PredictTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/custom_model_predict")
    val taskId = genTaskId(jobId)
    val task = module.predictTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = customModelJobConfig.cpus,
      memory   = customModelJobConfig.taskMemoryLimit,
      gpus     = customModelJobConfig.gpus
    )
    scheduler.submitTask(task)
  }
}
