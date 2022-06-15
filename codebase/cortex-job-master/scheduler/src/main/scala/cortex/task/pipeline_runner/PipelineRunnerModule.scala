package cortex.task.pipeline_runner

import cortex.task.pipeline_runner.PipelineRunnerParams.PipelineRunnerTaskParams
import cortex.task.task_creators.BinaryResultTask

class PipelineRunnerModule
  extends BinaryResultTask[PipelineRunnerTaskParams] {

  override val dockerImage: String = "deepcortex/cortex-tasks-gpu"
  override val module: String = "pipeline_runner"

}
