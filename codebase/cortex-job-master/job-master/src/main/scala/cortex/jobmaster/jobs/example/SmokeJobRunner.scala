package cortex.jobmaster.jobs.example

import java.util.UUID

import cortex.common.Logging
import cortex.common.logging.JMLoggerFactory
import cortex.rpc.TaskRPC
import cortex.scheduler.MesosTaskScheduler
import cortex.task.test.DelayTask

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class SmokeJobRunner(implicit val loggerFactory: JMLoggerFactory) extends Logging {

  protected val simpleDelayTask = DelayTask(
    UUID.randomUUID().toString,
    UUID.randomUUID().toString,
    0.5,
    64.0,
    DelayTask.DelayTaskParams(5)
  )

  def run(mesosMaster: String, taskRPC: TaskRPC, version: String, registry: String): Unit = {

    val scheduler = new MesosTaskScheduler(mesosMaster, taskRPC, version, registry)

    scheduler.start()

    val taskF = scheduler.submitTask(simpleDelayTask)
    val result = Await.result(taskF, 15.minutes)
    log.info(result.toString)
    log.info("Success")

    scheduler.stop()
  }
}
