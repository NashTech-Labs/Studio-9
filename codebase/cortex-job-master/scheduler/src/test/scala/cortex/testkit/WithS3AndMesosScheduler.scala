package cortex.testkit

import cortex.scheduler.MesosTaskScheduler
import org.scalatest.Suite

import scala.concurrent.ExecutionContext.Implicits.global

trait WithS3AndMesosScheduler extends WithS3TaskRPC with WithLogging {
  this: Suite =>

  protected val dockerImageVersion = "latest"
  protected val mesosMaster = "localhost:5050"
  protected var taskScheduler: MesosTaskScheduler = _

  abstract override def beforeAll(): Unit = {
    super.beforeAll()
    taskScheduler = new MesosTaskScheduler(mesosMaster, taskRPC, dockerImageVersion)
    taskScheduler.start()
  }

  abstract override def afterAll(): Unit = {
    super.afterAll()
    taskScheduler.stop()
  }
}
