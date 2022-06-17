package cortex.task.transform.splitter

import cortex.task.task_creators.{ GenericTask, TransformTaskCreator }
import cortex.task.transform.splitter.SplitterParams.{ SplitterTaskParams, SplitterTaskResult }
import play.api.libs.json.{ Reads, Writes }

class SplitterModule(
    splits:         Int,
    outputPrefix:   String,
    baseOutputPath: String
) extends TransformTaskCreator[SplitterTaskParams, SplitterTaskResult] {

  override val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val module: String = "splitter"

  override def transformTask(
    id:       String,
    jobId:    String,
    taskPath: String,
    params:   SplitterTaskParams,
    cpus:     Double,
    memory:   Double,
    gpus:     Int                = 0
  )(implicit reads: Reads[SplitterTaskResult], writes: Writes[SplitterTaskParams]): GenericTask[SplitterTaskResult, SplitterTaskParams] = {
    val internalParams = params.copy(
      outputBasePath = Some(params.outputBasePath.getOrElse(s"$baseOutputPath/$taskPath")),
      splits         = Some(params.splits.getOrElse(splits)),
      outputPrefix   = Some(params.outputPrefix.getOrElse(outputPrefix))
    )
    super.transformTask(id, jobId, taskPath, internalParams, cpus, memory, gpus)(reads, writes)
  }
}
