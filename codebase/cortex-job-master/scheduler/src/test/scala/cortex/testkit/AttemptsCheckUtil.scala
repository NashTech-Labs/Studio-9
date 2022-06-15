package cortex.testkit

import java.util.UUID

import cortex.scheduler.TaskScheduler
import cortex.task.test.AttemptTask.AttemptsCheckResult
import cortex.task.test.{ FailedAttemptTask, SucceedAttemptTask }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AttemptsCheckUtil {
  protected val succeedAttemptTask = SucceedAttemptTask(
    id       = UUID.randomUUID().toString,
    jobId    = UUID.randomUUID().toString,
    cpus     = 0.5,
    memory   = 64.0,
    attempts = 3
  )

  protected val failedAttemptTask = FailedAttemptTask(
    id       = UUID.randomUUID().toString,
    jobId    = UUID.randomUUID().toString,
    cpus     = 0.5,
    memory   = 64.0,
    attempts = 3
  )

  /**
   *
   * @return
   */
  def runSucceedTask(scheduler: TaskScheduler): Future[Unit] = {
    val attemptsCheckResultF = scheduler.submitTask(succeedAttemptTask)
    for {
      result <- attemptsCheckResultF
    } yield if (result.taskId != succeedAttemptTask.id) throw new RuntimeException("task id's don't match")
  }

  def runFailedTask(scheduler: TaskScheduler): Future[Unit] = {
    //here we don't care about the result, so just map to unit
    for {
      _ <- scheduler.submitTask(failedAttemptTask)
    } yield ()
  }
}
