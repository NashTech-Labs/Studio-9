package cortex.task.video_uploading

import cortex.task.task_creators.TransformTaskCreator
import cortex.task.video_uploading.VideoUploadingParams.{ VideoImportTaskParams, VideoImportTaskResult }

class VideoUploadingModule extends TransformTaskCreator[VideoImportTaskParams, VideoImportTaskResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-gpu"
  override val module: String = "video_import"

}
