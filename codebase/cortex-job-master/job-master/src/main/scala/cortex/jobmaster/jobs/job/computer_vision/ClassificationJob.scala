package cortex.jobmaster.jobs.job.computer_vision

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.computer_vision.ClassificationModule
import cortex.task.computer_vision.ClassificationParams._

import scala.concurrent.{ ExecutionContext, Future }

class ClassificationJob(
    scheduler:               TaskScheduler,
    module:                  ClassificationModule,
    classificationJobConfig: ClassificationJobConfig
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  def train(
    jobId:          String,
    params:         CVTrainTaskParams,
    customTaskPath: Option[String]    = None
  ): Future[CVTrainTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/comp_vision_train")
    val taskId = genTaskId(jobId)
    val task = module.trainTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = classificationJobConfig.cpus,
      memory   = classificationJobConfig.taskMemoryLimit,
      gpus     = classificationJobConfig.gpus
    )
    scheduler.submitTask(task)
  }

  def score(
    jobId:          String,
    params:         CVScoreTaskParams,
    customTaskPath: Option[String]    = None
  ): Future[CVScoreTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/comp_vision_score")
    val taskId = genTaskId(jobId)
    val task = module.scoreTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = classificationJobConfig.cpus,
      memory   = classificationJobConfig.taskMemoryLimit,
      gpus     = classificationJobConfig.gpus
    )
    scheduler.submitTask(task)
  }

  def predict(
    jobId:          String,
    params:         CVPredictTaskParams,
    customTaskPath: Option[String]      = None
  ): Future[CVPredictTaskResult] = {
    val taskPath = customTaskPath.getOrElse(s"$jobId/comp_vision_predict")
    val taskId = genTaskId(jobId)
    val task = module.predictTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = classificationJobConfig.cpus,
      memory   = classificationJobConfig.taskMemoryLimit,
      gpus     = classificationJobConfig.gpus
    )
    scheduler.submitTask(task)
  }
}
