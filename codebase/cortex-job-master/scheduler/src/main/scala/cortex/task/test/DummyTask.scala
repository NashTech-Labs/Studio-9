package cortex.task.test

import java.nio.charset.Charset

import cortex.JsonSupport.SnakeJson
import cortex.task.test.DummyTask.DummyTaskResult
import cortex.{ JsonSupport, Task, TaskParams, TaskResult }
import play.api.libs.json.Reads

class DummyTask(
    override val id:     String,
    override val jobId:  String,
    override val cpus:   Double,
    override val memory: Double
) extends Task[DummyTaskResult, TaskParams] {
  private implicit val reads: Reads[DummyTaskResult] = DummyTask.dummyTaskResultReads
  override val dockerImage = "deepcortex/cortex-tasks-sklearn"
  override val module = "dummy"
  override val taskPath = s"$jobId/dummy"

  override def getSerializedParams: Array[Byte] = Array.empty[Byte]

  override def parseResult(payload: Array[Byte]): DummyTaskResult =
    JsonSupport.fromString(new String(payload, Charset.forName("UTF-8")))
}

object DummyTask {

  case class DummyTaskResult(taskId: String) extends TaskResult

  val dummyTaskResultReads: Reads[DummyTaskResult] = SnakeJson.reads[DummyTaskResult]
}
