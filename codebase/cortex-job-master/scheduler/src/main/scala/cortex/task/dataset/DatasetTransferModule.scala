package cortex.task.dataset
import cortex.task.dataset.DatasetTransferParams.{ DatasetTransferTaskParams, DatasetTransferTaskResult }
import cortex.task.task_creators.TransformTaskCreator

class DatasetTransferModule extends TransformTaskCreator[DatasetTransferTaskParams, DatasetTransferTaskResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val module: String = "dataset_transfer"

}
