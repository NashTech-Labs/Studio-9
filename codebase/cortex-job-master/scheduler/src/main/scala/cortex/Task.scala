package cortex

import java.util.Date

import play.api.libs.json.{ JsSuccess, Reads }

trait TaskParams

object TaskParams {
  case class BinaryParams(bytes: Array[Byte]) extends TaskParams
}

trait TaskResult {
  var taskTimeInfo: TaskTimeInfo = _
}

object TaskResult {

  case class Empty() extends TaskResult

  case class BinaryResult(bytes: Array[Byte]) extends TaskResult

  implicit val emptyTaskResultReads: Reads[Empty] = Reads[Empty](_ => JsSuccess(Empty()))
}

case class TaskTimeInfo(
    taskId:      String,
    submittedAt: Date,
    startedAt:   Option[Date] = None,
    completedAt: Option[Date] = None
)

trait Task[T <: TaskResult, P <: TaskParams] {
  self =>

  // task id
  val id: String

  // job id
  val jobId: String

  //task work path
  val taskPath: String

  // task parameters
  private var params: P = _

  def setParams(params: P): Unit =
    self.params = params

  def getParams: P =
    self.params

  // required cpu resources
  val cpus: Double

  // required gpu resources
  val gpus: Int = 0

  // required memory resources
  val memory: Double

  // Docker image for a task
  val dockerImage: String

  // Module of a task in docker container. Ex. logistic or splitter.
  val module: String

  // task state
  private var state: TaskState = TaskState.Pending

  def setState(state: TaskState): Unit =
    self.state = state

  def getState: TaskState =
    self.state

  //attempts to run this task
  // todo: task should not know how many times to retry it. Scheduler should know that.
  private var attempts: Int = 3

  def setAttempts(attempts: Int): Unit =
    self.attempts = attempts

  def decreaseAttempts(): Unit =
    self.attempts -= 1

  def getAttempts: Int =
    self.attempts

  // Command to run inside docker container
  def command: Seq[String] =
    Seq(
      s"--task_id=$id",
      s"--job_id=$jobId",
      s"--module=$module"
    )

  // Serialize params
  def getSerializedParams: Array[Byte]

  // Parse results from raw task output
  def parseResult(payload: Array[Byte]): T
}

sealed trait TaskState

object TaskState {

  // to be started
  case object Pending extends TaskState

  // in process of starting
  case object Staging extends TaskState

  case object Running extends TaskState

  case object Successful extends TaskState // terminal

  case object Failed extends TaskState // terminal

  case object Killed extends TaskState // terminal

  def isFailed(state: TaskState): Boolean = {
    state == Failed
  }

  def isFinished(state: TaskState): Boolean = {
    (state == Successful) || (state == Failed) ||
      (state == Killed)
  }
}
