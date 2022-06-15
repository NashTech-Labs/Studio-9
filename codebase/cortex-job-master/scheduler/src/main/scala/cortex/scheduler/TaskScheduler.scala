package cortex.scheduler

import cortex.rpc.TaskRPC
import cortex.{ Task, TaskParams, TaskResult }

import scala.concurrent.Future

trait TaskScheduler {

  type TaskID = String

  def taskRPC: TaskRPC

  def submitTask[T <: TaskResult](task: Task[T, _ <: TaskParams]): Future[T]

  def cancelTask(taskId: String): Unit

  def stop(): Unit

  def dockerImageVersion: String

  def dockerImageRegistry: String

}
