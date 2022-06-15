package cortex.task.test

import java.nio.charset.Charset

import cortex.JsonSupport.SnakeJson
import cortex.task.test.GpuTestTask.{ GpuTestTaskParams, GpuTestTaskResult }
import cortex.{ JsonSupport, Task, TaskParams, TaskResult }
import play.api.libs.json.{ Reads, Writes }

class GpuTestTask(
    override val id:     String,
    override val jobId:  String,
    override val cpus:   Double,
    override val gpus:   Int,
    override val memory: Double
) extends Task[GpuTestTaskResult, GpuTestTaskParams] {
  private implicit val reads: Reads[GpuTestTaskResult] = GpuTestTask.gpuTestTaskResultReads
  private implicit val writes: Writes[GpuTestTaskParams] = GpuTestTask.gpuTestTaskParamsWrites
  override val dockerImage = "deepcortex/cortex-tasks-gpu"
  override val module = "gpu"
  override val taskPath = s"$jobId/gpu"

  override def getSerializedParams: Array[Byte] =
    JsonSupport.toString(getParams).getBytes(Charset.forName("UTF-8"))

  override def parseResult(payload: Array[Byte]): GpuTestTaskResult =
    JsonSupport.fromString(new String(payload, Charset.forName("UTF-8")))
}

object GpuTestTask {
  def apply(
    id:       String,
    jobId:    String,
    cpus:     Double,
    gpus:     Int,
    memory:   Double,
    params:   GpuTestTaskParams,
    attempts: Int               = 1
  ): GpuTestTask = {
    val task = new GpuTestTask(id, jobId, cpus, gpus, memory)
    task.setParams(params)
    task.setAttempts(attempts)
    task
  }

  case class GpuTestTaskParams(taskDuration: Int) extends TaskParams
  case class GpuTestTaskResult(taskId: String) extends TaskResult

  val gpuTestTaskParamsWrites: Writes[GpuTestTaskParams] = SnakeJson.writes[GpuTestTaskParams]
  val gpuTestTaskResultReads: Reads[GpuTestTaskResult] = SnakeJson.reads[GpuTestTaskResult]
}
