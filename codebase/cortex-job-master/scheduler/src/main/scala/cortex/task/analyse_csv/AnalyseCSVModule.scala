package cortex.task.analyse_csv

import cortex.task.analyse_csv.AnalyseCSVParams._
import cortex.task.task_creators.TransformTaskCreator

class AnalyseCSVModule extends TransformTaskCreator[AnalyseCSVTaskParams, AnalyseCSVTaskResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val module: String = "analyse_csv"

}
