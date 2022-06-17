package cortex.jobmaster.jobs.job.computer_vision

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.computer_vision.StackedAutoencoderModule
import cortex.task.computer_vision.AutoencoderParams._

import scala.concurrent.{ ExecutionContext, Future }

class AutoencoderJob(
    scheduler:            TaskScheduler,
    module:               StackedAutoencoderModule, //todo replace with AutoencoderModule when other encoders are appeared
    autoencoderJobConfig: AutoencoderJobConfig
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  def train(
    jobId:          String,
    params:         AutoencoderTrainTaskParams,
    customTaskPath: Option[String]             = None
  ): Future[AutoencoderTrainTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/autoencoder_train")
    val taskId = genTaskId(jobId)
    val task = module.trainTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = autoencoderJobConfig.cpus,
      memory   = autoencoderJobConfig.taskMemoryLimit,
      gpus     = autoencoderJobConfig.gpus
    )
    scheduler.submitTask(task)
  }

  def predict(
    jobId:          String,
    params:         AutoencoderPredictTaskParams,
    customTaskPath: Option[String]               = None
  ): Future[AutoencoderPredictTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/autoencoder_predict")
    val taskId = genTaskId(jobId)
    val task = module.predictTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = autoencoderJobConfig.cpus,
      memory   = autoencoderJobConfig.taskMemoryLimit,
      gpus     = autoencoderJobConfig.gpus
    )
    scheduler.submitTask(task)
  }

  def score(
    jobId:          String,
    params:         AutoencoderScoreTaskParams,
    customTaskPath: Option[String]             = None
  ): Future[AutoencoderScoreTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/autoencoder_score")
    val taskId = genTaskId(jobId)
    val task = module.scoreTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = autoencoderJobConfig.cpus,
      memory   = autoencoderJobConfig.taskMemoryLimit,
      gpus     = autoencoderJobConfig.gpus
    )
    scheduler.submitTask(task)
  }
}
