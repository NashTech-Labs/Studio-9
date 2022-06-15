package cortex.jobmaster.jobs.job.computer_vision

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.computer_vision.LocalizationModule
import cortex.task.computer_vision.LocalizationParams._

import scala.concurrent.{ ExecutionContext, Future }

class LocalizationJob(
    scheduler:             TaskScheduler,
    module:                LocalizationModule,
    localizationJobConfig: LocalizationJobConfig
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  def train(
    jobId:          String,
    params:         TrainTaskParams,
    customTaskPath: Option[String]  = None
  ): Future[TrainTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/localization_train")
    val taskId = genTaskId(jobId)
    val task = module.trainTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = localizationJobConfig.cpus,
      memory   = localizationJobConfig.taskMemoryLimit,
      gpus     = localizationJobConfig.gpus
    )
    scheduler.submitTask(task)
  }

  def score(
    jobId:          String,
    params:         ScoreTaskParams,
    customTaskPath: Option[String]  = None
  ): Future[ScoreTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/localization_score")
    val taskId = genTaskId(jobId)
    val task = module.scoreTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = localizationJobConfig.cpus,
      memory   = localizationJobConfig.taskMemoryLimit,
      gpus     = localizationJobConfig.gpus
    )
    scheduler.submitTask(task)
  }

  def predict(
    jobId:          String,
    params:         PredictTaskParams,
    customTaskPath: Option[String]    = None
  ): Future[PredictTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/localization_predict")
    val taskId = genTaskId(jobId)
    val task = module.predictTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = localizationJobConfig.cpus,
      memory   = localizationJobConfig.taskMemoryLimit,
      gpus     = localizationJobConfig.gpus
    )
    scheduler.submitTask(task)
  }

  def composeVideo(
    jobId:          String,
    params:         ComposeVideoTaskParams,
    customTaskPath: Option[String]         = None
  ): Future[ComposeVideoTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/localization_video")
    val taskId = genTaskId(jobId)
    val task = module.composeVideoTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = localizationJobConfig.cpus,
      memory   = localizationJobConfig.composeVideoTaskMemoryLimit
    )
    scheduler.submitTask(task)
  }
}
