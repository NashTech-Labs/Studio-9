package cortex.jobmaster.jobs.example

import java.util.UUID

import cortex.common.Logging
import cortex.common.logging.JMLoggerFactory
import cortex.rpc.TaskRPC
import cortex.scheduler.MesosTaskScheduler
import cortex.task.test.GpuTestTask

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SmokeGpuJobRunner(implicit val loggerFactory: JMLoggerFactory) extends Logging {

  protected val gpuTestTask = GpuTestTask(
    UUID.randomUUID().toString,
    UUID.randomUUID().toString,
    1,
    1,
    2018.0,
    GpuTestTask.GpuTestTaskParams(5)
  )

  def run(mesosMaster: String, taskRPC: TaskRPC, version: String, registry: String): Unit = {

    val scheduler = new MesosTaskScheduler(mesosMaster, taskRPC, version, registry)

    scheduler.start()

    val taskResultFuture = scheduler.submitTask(gpuTestTask)
    val result = Await.result(taskResultFuture, 15.minutes)
    log.info(result.toString)
    log.info("Success")

    scheduler.stop()
  }
}
