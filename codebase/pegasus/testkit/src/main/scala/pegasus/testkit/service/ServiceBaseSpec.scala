package pegasus.testkit.service

import java.util.{ Date, UUID }

import akka.actor.{ ActorRef, ActorSystem, Status }
import akka.testkit.{ CallingThreadDispatcher, DefaultTimeout, ImplicitSender, TestKit, TestKitBase, TestProbe }
import pegasus.common.service.{ DateImplicits, DateSupport, Service, UUIDSupport }
import pegasus.testkit.BaseSpec
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, Suite }

trait ServiceBaseSpec extends ImplicitSystem
    with TestKitBase
    with BaseSpec
    with DefaultTimeout
    with ImplicitSender
    with StopSystemAfterAll
    with MockFactory
    with DateSupportMocks
    with UUIDSupportMocks {

  trait ServiceScope extends FailureExpectationSupport {
    val service: ActorRef

    // CallingThreadDispatcher is used here to make testing execution synchronous
    implicit val ec = system.dispatchers.lookup(CallingThreadDispatcher.Id)

    trait DateSupportTesting extends DateSupport { self: Service =>
      override def currentDate(): Date = mockCurrentDate
    }

    trait UUIDSupportTesting extends UUIDSupport { self: Service =>
      override def randomUUID(): UUID = mockRandomUUID
    }

  }

  trait FailureExpectationSupport { self: ServiceScope =>
    implicit class ExtendedProbe(testProbe: TestProbe) {
      def replyWithFailure(t: Throwable): Unit = {
        testProbe.reply(Status.Failure(t))
      }
    }

    def expectMsgFailure(t: Throwable): Status.Failure = {
      expectMsg(Status.Failure(t))
    }

    def expectMsgFailurePF(f: PartialFunction[Throwable, Throwable]): Throwable = {
      expectMsgPF() {
        case Status.Failure(t) => {
          assert(f.isDefinedAt(t), s"unexpected throwable: $t")
          f(t)
        }
      }
    }
  }

}

trait DateSupportMocks extends DateImplicits {
  val mockCurrentDate: Date = new Date().withoutMillis()
}

trait UUIDSupportMocks {
  val mockRandomUUID: UUID = UUID.randomUUID()
}

trait ImplicitSystem { self: TestKitBase =>
  implicit val system = ActorSystem(systemName)

  private def systemName() = s"${this.getClass().getSimpleName()}-System"
}

trait StopSystemAfterAll extends BeforeAndAfterAll { self: TestKitBase with Suite =>
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
