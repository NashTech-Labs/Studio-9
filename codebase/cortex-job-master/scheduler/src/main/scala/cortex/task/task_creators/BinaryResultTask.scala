package cortex.task.task_creators

import cortex.TaskParams
import cortex.TaskResult.BinaryResult
import play.api.libs.json.Writes

trait BinaryResultTask[P <: TaskParams] {

  def dockerImage: String

  def module: String

  def createTask(
    id:       String,
    jobId:    String,
    taskPath: String,
    params:   P,
    cpus:     Double,
    memory:   Double,
    gpus:     Int    = 0
  )(implicit writes: Writes[P]): GenericTask[BinaryResult, P] = {
    GenericTask(
      id           = id,
      jobId        = jobId,
      params       = params,
      cpus         = cpus,
      memory       = memory,
      taskModule   = module,
      taskBasePath = taskPath,
      image        = dockerImage,
      gpus         = gpus,
      serialize    = GenericTask.defaultSerialize,
      deserialize  = BinaryResult
    )
  }
}
