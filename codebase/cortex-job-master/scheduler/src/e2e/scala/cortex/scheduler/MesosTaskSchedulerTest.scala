package cortex.scheduler

import java.util.UUID

import cortex.task.test.DelayTask
import cortex.task.test.DelayTask.DelayTaskParams
import cortex.testkit.{FutureTestUtils, WithS3AndMesosScheduler}
import cortex.{CortexException, TaskState}
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar._


class MesosTaskSchedulerTest extends FlatSpec
  with FutureTestUtils
  with Eventually
  with WithS3AndMesosScheduler {

  val longTask = DelayTask(
    id = UUID.randomUUID().toString,
    jobId = UUID.randomUUID().toString,
    cpus = 0.5, memory = 64.0,
    params = DelayTaskParams(taskDuration = 60),
    attempts = 3
  )

  val fastTask = DelayTask(
    id = UUID.randomUUID().toString,
    jobId = UUID.randomUUID().toString,
    cpus     = 0.5,
    memory   = 64.0,
    params   = DelayTaskParams(taskDuration = 0),
    attempts = 3
  )

  "MesosTaskScheduler" should "kill canceled task" in {

    val taskResult = taskScheduler.submitTask(longTask)

    // wait till job is started by mesos
    eventually(timeout(10.seconds)) {
      assert(longTask.getState == TaskState.Running)
    }

    taskScheduler.cancelTask(longTask.id)

    eventually(timeout(10.seconds)) {
      assert(longTask.getState == TaskState.Killed)
    }

    intercept[CortexException] {
      taskResult.await()
    }
  }

  it should "successfully finish task if not cancelled" in {
    val future = taskScheduler.submitTask(fastTask)
    val result = future.await()
    result.taskId shouldBe fastTask.id
  }

}
