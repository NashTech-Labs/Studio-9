package cortex.jobmaster.jobs.job

import cortex.jobmaster.jobs.TaskIdGenerator
import cortex.scheduler.TaskScheduler
import cortex.task.data_augmentation.DataAugmentationParams._
import cortex.task.task_creators.TransformTaskCreator

import scala.concurrent.{ ExecutionContext, Future }

// todo: move all specific modules to the same files as jobs because:
// 1. they are never substituted in as mocks in jobs
// 2. all the parameters that are specified in modules should be known on a job level (like job base path)
//
// Opposite idea: leave as is in other places and add as a constructor arguments here.
// In this case we can test without using task scheduler, because mock modules will not use it.
// or we can mock then by overriding job
class DataAugmentationModule extends TransformTaskCreator[TransformParams, TransformResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val module: String = "manual_data_augmentation"
}

class ImagesCopyModule extends TransformTaskCreator[ImagesCopyParams, ImagesCopyResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val module: String = "images_copy"
}

class DataAugmentationJob(
    scheduler:                 TaskScheduler,
    dataAugmentationJobConfig: DataAugmentationJobConfig // todo: investigate memory leaks in task
)(implicit val context: ExecutionContext) extends TaskIdGenerator {

  private val augmentationModule = new DataAugmentationModule

  private val albumCopyModule = new ImagesCopyModule

  def transform(
    jobId:          String,
    params:         TransformParams,
    customTaskPath: Option[String]  = None
  ): Future[TransformResult] = {
    val taskId = genTaskId(jobId)
    val taskPath = customTaskPath.getOrElse(s"$jobId/data_augmentation/$taskId")
    val task = augmentationModule.transformTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = dataAugmentationJobConfig.cpus,
      memory   = dataAugmentationJobConfig.taskMemoryLimit
    )
    scheduler.submitTask(task)
  }

  def copyImages(
    jobId:          String,
    params:         ImagesCopyParams,
    customTaskPath: Option[String]   = None
  ): Future[ImagesCopyResult] = {
    val taskId = genTaskId(jobId)
    val taskPath = customTaskPath.getOrElse(s"$jobId/data_augmentation/$taskId")
    val task = albumCopyModule.transformTask(
      id       = taskId,
      jobId    = jobId,
      taskPath = taskPath,
      params   = params,
      cpus     = dataAugmentationJobConfig.cpus,
      memory   = dataAugmentationJobConfig.taskMemoryLimit
    )
    scheduler.submitTask(task)
  }

}
