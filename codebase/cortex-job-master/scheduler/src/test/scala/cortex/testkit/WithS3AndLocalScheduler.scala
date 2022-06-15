package cortex.testkit

import com.typesafe.config.ConfigFactory
import cortex.scheduler.LocalTaskScheduler
import org.scalatest.Suite

import scala.concurrent.ExecutionContext.Implicits.global

trait WithS3AndLocalScheduler extends WithS3TaskRPC with WithLogging {
  this: Suite =>

  private val config = ConfigFactory.load()
  private val dockerImageVersion = config.getConfig("cortex-job-master-tasks").getString("version")

  protected var taskScheduler: LocalTaskScheduler {
    def stopInvocations(): Int
  } = _

  abstract override def beforeAll(): Unit = {
    super.beforeAll()
    taskScheduler = new LocalTaskScheduler(taskRPC, dockerImageVersion) {
      private var _stop = 0

      override def stop(): Unit = {
        super.stop()
        _stop += 1
      }

      def stopInvocations(): Int = _stop
    }
  }
}
