package taurus.testkit.service

import akka.testkit.TestActorRef
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{ Millis, Span }
import taurus.common.service.{ Service, TimerSupport }

import scala.concurrent.duration._

//scalastyle:off
class TimerSupportSpec extends ServiceBaseSpec {
  import TimerSupportSpec._

  implicit val patienceConfig = Eventually.PatienceConfig(
    timeout  = Span(2 * timerTriggerValueMs, Millis),
    interval = Span(50, Millis)
  )

  "Timer support extension" must {
    "invoke onTimerTrigger" in {
      val service = TestActorRef(new TestService())
      service.underlyingActor.startTimer()
      Eventually.eventually {
        service.underlyingActor.cntCount shouldBe 1
      }
    }

    "should do nothing when timer was not started" in {
      val service = TestActorRef(new TestService())
      delay()
      service.underlyingActor.cntCount shouldBe 0
    }

    "should stop timer" in {
      val service = TestActorRef(new TestService())
      service.underlyingActor.startTimer()
      service.underlyingActor.stopTimer()
      delay()
      service.underlyingActor.cntCount shouldBe 0
    }

    "should reset timer" in {
      val service = TestActorRef(new TestService())
      service.underlyingActor.startTimer()
      delay()
      service.underlyingActor.restartTimer()
      Eventually.eventually {
        service.underlyingActor.cntCount shouldBe 2
      }
    }

    "should throw an exception when timer was already started" in {
      val service = TestActorRef(new TestService())
      service.underlyingActor.startTimer()
      intercept[IllegalStateException](service.underlyingActor.startTimer())
    }

    def delay() = {
      // TODO probably get rid of thread sleep
      Thread.sleep(timerTriggerValueMs * 2)
    }
  }
}

object TimerSupportSpec {

  val timerTriggerValueMs = 200L

  class TestService extends Service with TimerSupport {
    private var cnt = 0
    override val timerValue: FiniteDuration = timerTriggerValueMs.millis
    override def onTimerTrigger(): Unit = { cnt += 1 }

    def cntCount: Int = { cnt }
    def resetCount(): Unit = { cnt = 0 }
  }
}
