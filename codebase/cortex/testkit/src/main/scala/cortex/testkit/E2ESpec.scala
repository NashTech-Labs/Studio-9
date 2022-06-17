package cortex.testkit

import akka.testkit.{ CallingThreadDispatcher, DefaultTimeout, TestKitBase }
import cortex.testkit.service.{ ImplicitSystem, StopSystemAfterAll }
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext

trait E2ESpec extends ImplicitSystem with TestKitBase with DefaultTimeout with BaseSpec with StopSystemAfterAll with ScalaFutures with AkkaPatienceConfiguration with HttpClientSupport with RabbitMqClientSupport {

  trait BaseScope {
    // CallingThreadDispatcher is used here to make testing execution synchronous
    implicit val executor: ExecutionContext = system.dispatchers.lookup(CallingThreadDispatcher.Id)
  }
}
