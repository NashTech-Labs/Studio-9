package cortex.task.test

import java.nio.charset.Charset

import cortex.JsonSupport.SnakeJson
import cortex._
import cortex.task.test.AttemptTask.{ AttemptsCheckParams, AttemptsCheckResult }
import play.api.libs.json.{ Reads, Writes }

/**
 * The classes below are used only for test purposes, to check attempts behaviour
 */
class SucceedAttemptTask private (
    override val id:     String,
    override val jobId:  String,
    override val cpus:   Double,
    override val memory: Double
) extends Task[AttemptsCheckResult, AttemptsCheckParams] {
  private implicit val reads: Reads[AttemptsCheckResult] = AttemptTask.attemptsCheckResultReads
  private implicit val writes: Writes[AttemptsCheckParams] = AttemptTask.attemptsCheckParamsWrites
  override val module = "attempts_check"

  val dockerImage: String = "deepcortex/cortex-tasks-sklearn"
  override val taskPath = s"$jobId/succeed_attempt"

  override def decreaseAttempts(): Unit = {
    super.decreaseAttempts()
    this.setParams(params = AttemptsCheckParams(this.getAttempts > 1))
  }

  override def getSerializedParams: Array[Byte] =
    JsonSupport.toString(getParams).getBytes(Charset.forName("UTF-8"))

  override def parseResult(payload: Array[Byte]): AttemptsCheckResult =
    JsonSupport.fromString(new String(payload, Charset.forName("UTF-8")))
}

class FailedAttemptTask private (
    override val id:     String,
    override val jobId:  String,
    override val cpus:   Double,
    override val memory: Double
) extends Task[AttemptsCheckResult, AttemptsCheckParams] {
  private implicit val reads: Reads[AttemptsCheckResult] = AttemptTask.attemptsCheckResultReads
  private implicit val writes: Writes[AttemptsCheckParams] = AttemptTask.attemptsCheckParamsWrites
  override val module = "attempts_check"

  val dockerImage: String = "deepcortex/cortex-tasks-sklearn"

  override val taskPath = s"$jobId/failed_attempt"

  override def getSerializedParams: Array[Byte] =
    JsonSupport.toString(getParams).getBytes(Charset.forName("UTF-8"))

  override def parseResult(payload: Array[Byte]): AttemptsCheckResult =
    JsonSupport.fromString(new String(payload, Charset.forName("UTF-8")))
}

object FailedAttemptTask {
  def apply(
    id:       String,
    jobId:    String,
    cpus:     Double,
    memory:   Double,
    attempts: Int,
    params:   AttemptsCheckParams = AttemptsCheckParams()
  ): FailedAttemptTask = {
    val task = new FailedAttemptTask(id, jobId, cpus, memory)
    task.setParams(params)
    task.setAttempts(attempts)
    task
  }
}

object SucceedAttemptTask {
  def apply(
    id:       String,
    jobId:    String,
    cpus:     Double,
    memory:   Double,
    attempts: Int,
    params:   AttemptsCheckParams = AttemptsCheckParams()
  ): SucceedAttemptTask = {
    val task = new SucceedAttemptTask(id, jobId, cpus, memory)
    task.setParams(params)
    task.setAttempts(attempts)
    task
  }
}

object AttemptTask {

  case class AttemptsCheckResult(taskId: String) extends TaskResult

  case class AttemptsCheckParams(fail: Boolean = true) extends TaskParams

  val attemptsCheckParamsWrites: Writes[AttemptsCheckParams] = SnakeJson.writes[AttemptsCheckParams]
  val attemptsCheckResultReads: Reads[AttemptsCheckResult] = SnakeJson.reads[AttemptsCheckResult]
}
