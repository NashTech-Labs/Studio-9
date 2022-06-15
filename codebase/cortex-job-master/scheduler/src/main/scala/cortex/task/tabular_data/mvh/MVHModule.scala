package cortex.task.tabular_data.mvh

import cortex.task.tabular_data.mvh.MVHParams.{ MVHTrainParams, MVHTrainResult }
import cortex.task.task_creators.TrainTaskCreator

class MVHModule(baseOutputPath: String) extends TrainTaskCreator[MVHTrainParams, MVHTrainResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-gpu"
  override val module: String = "mvh"

}
