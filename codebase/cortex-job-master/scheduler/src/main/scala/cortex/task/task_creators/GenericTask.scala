package cortex.task.task_creators

import java.nio.charset.Charset

import cortex.{ JsonSupport, Task, TaskParams, TaskResult }
import play.api.libs.json.{ Reads, Writes }

abstract class GenericTask[T <: TaskResult, P <: TaskParams] private (
    override val id:     String,
    override val jobId:  String,
    override val cpus:   Double,
    override val gpus:   Int,
    override val memory: Double
) extends Task[T, P] {
}

object GenericTask {
  private[task] def apply[T <: TaskResult: Reads, P <: TaskParams: Writes](
    id:           String,
    jobId:        String,
    params:       P,
    cpus:         Double,
    memory:       Double,
    taskModule:   String,
    taskBasePath: String,
    image:        String,
    gpus:         Int
  ): GenericTask[T, P] = {
    GenericTask(
      id           = id,
      jobId        = jobId,
      params       = params,
      cpus         = cpus,
      memory       = memory,
      taskModule   = taskModule,
      taskBasePath = taskBasePath,
      image        = image,
      gpus         = gpus,
      serialize    = defaultSerialize,
      deserialize  = defaultDeserialize
    )
  }

  // scalastyle:off parameter.number
  private[task] def apply[T <: TaskResult, P <: TaskParams](
    id:           String,
    jobId:        String,
    params:       P,
    cpus:         Double,
    memory:       Double,
    taskModule:   String,
    taskBasePath: String,
    image:        String,
    gpus:         Int,
    serialize:    P => Array[Byte],
    deserialize:  Array[Byte] => T
  ): GenericTask[T, P] = {
    val task: GenericTask[T, P] = new GenericTask[T, P](id, jobId, cpus, gpus, memory) {
      override val dockerImage: String = image
      override val module: String = taskModule
      override val taskPath: String = taskBasePath

      override def getSerializedParams: Array[Byte] = serialize(getParams)

      override def parseResult(payload: Array[Byte]): T = deserialize(payload)
    }
    task.setParams(params)
    task
  }
  // scalastyle:on parameter.number

  private[task] def defaultSerialize[P <: TaskParams: Writes]: P => Array[Byte] =
    (params: P) => JsonSupport.toString(params).getBytes(Charset.forName("UTF-8"))

  private[task] def defaultDeserialize[T <: TaskResult: Reads]: Array[Byte] => T =
    (payload: Array[Byte]) => JsonSupport.fromString(new String(payload, Charset.forName("UTF-8")))
}
