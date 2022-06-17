package cortex.scheduler

import java.util.UUID

import cortex.CortexException
import cortex.task.test.DelayTask
import cortex.task.test.DelayTask.DelayTaskParams
import cortex.testkit.{ FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class LocalSchedulerTest extends FlatSpec
  with FutureTestUtils
  with WithS3AndLocalScheduler {

  val longTask = DelayTask(
    id       = UUID.randomUUID().toString,
    jobId    = UUID.randomUUID().toString,
    cpus     = 0.5,
    memory   = 64.0,
    params   = DelayTaskParams(taskDuration = 5),
    attempts = 3
  )

  val fastTask = DelayTask(
    id       = UUID.randomUUID().toString,
    jobId    = UUID.randomUUID().toString,
    cpus     = 0.5,
    memory   = 64.0,
    params   = DelayTaskParams(taskDuration = 0),
    attempts = 3
  )

  "LocalScheduler" should "kill cancelled task" in {
    val taskResult = taskScheduler.submitTask(longTask)
    taskScheduler.cancelTask(longTask.id)

    assertThrows[CortexException] {
      taskResult.await()
    }
  }

  it should "successfully finish task if not cancelled" in {
    val future = taskScheduler.submitTask(fastTask)
    val result = future.await()
    result.taskId shouldBe fastTask.id
  }
}
