package cortex.task.image_uploading

import cortex.task.image_uploading.ImageUploadingParams.{ S3ImportTaskParams, S3ImportTaskResult }
import cortex.task.task_creators.TransformTaskCreator

class ImageUploadingModule extends TransformTaskCreator[S3ImportTaskParams, S3ImportTaskResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val module: String = "images_s3_import"

}
