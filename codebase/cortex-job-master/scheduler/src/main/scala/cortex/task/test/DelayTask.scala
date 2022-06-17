package cortex.task.test

import java.nio.charset.Charset

import cortex.JsonSupport.SnakeJson
import cortex.task.test
import cortex.task.test.DelayTask.{ DelayTaskParams, DelayTaskResults }
import cortex.{ JsonSupport, Task, TaskParams, TaskResult }
import play.api.libs.json.{ Reads, Writes }

/**
 * Used only for test purposes. This task simply works not less than N seconds
 * see param [[test.DelayTask.DelayTaskParams]]
 */

class DelayTask private (
    override val id:     String,
    override val jobId:  String,
    override val cpus:   Double,
    override val memory: Double
) extends Task[DelayTaskResults, DelayTaskParams] {
  private implicit val reads: Reads[DelayTaskResults] = DelayTask.delayTaskResultsReads
  private implicit val writes: Writes[DelayTaskParams] = DelayTask.delayTaskParamsWrites
  override val dockerImage = "deepcortex/cortex-tasks-sklearn"
  override val module = "delayer"
  override val taskPath = s"$jobId/delay"

  override def getSerializedParams: Array[Byte] =
    JsonSupport.toString(getParams).getBytes(Charset.forName("UTF-8"))

  override def parseResult(payload: Array[Byte]): DelayTaskResults =
    JsonSupport.fromString(new String(payload, Charset.forName("UTF-8")))
}

object DelayTask {
  def apply(
    id:       String,
    jobId:    String,
    cpus:     Double,
    memory:   Double,
    params:   DelayTaskParams,
    attempts: Int             = 1
  ): DelayTask = {
    val task = new DelayTask(id, jobId, cpus, memory)
    task.setParams(params)
    task.setAttempts(attempts)
    task
  }

  /**
   *
   * @param taskDuration duration of this task in seconds
   */
  case class DelayTaskParams(taskDuration: Int) extends TaskParams

  case class DelayTaskResults(taskId: String) extends TaskResult

  val delayTaskParamsWrites: Writes[DelayTaskParams] = SnakeJson.writes[DelayTaskParams]
  val delayTaskResultsReads: Reads[DelayTaskResults] = SnakeJson.reads[DelayTaskResults]
}
