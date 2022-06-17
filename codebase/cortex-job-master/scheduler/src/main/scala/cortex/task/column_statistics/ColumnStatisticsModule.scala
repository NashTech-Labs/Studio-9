package cortex.task.column_statistics

import cortex.task.column_statistics.ColumnStatisticsParams._
import cortex.task.task_creators.TransformTaskCreator

class ColumnStatisticsModule extends TransformTaskCreator[ColumnStatisticsTaskParams, ColumnStatisticsTaskResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val module: String = "calculate_statistics"

}
