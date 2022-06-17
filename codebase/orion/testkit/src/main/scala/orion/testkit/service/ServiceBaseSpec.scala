package orion.testkit.service

import java.util.{ Date, UUID }

import akka.actor.{ ActorRef, ActorSystem, Status }
import akka.testkit.{ CallingThreadDispatcher, DefaultTimeout, ImplicitSender, TestKit, TestKitBase }
import akka.util.Timeout
import com.rabbitmq.client.AMQP.Queue.DeleteOk
import com.spingo.op_rabbit.{ Message, RecoveryStrategy, SubscriptionRef }
import org.json4s.Formats
import org.scalamock.handlers._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, Suite }
import orion.common.service._
import orion.ipc.rabbitmq.{ RabbitMqRpcClient, RabbitMqRpcClientSupport }
import orion.testkit.BaseSpec
import mesosphere.marathon.client.model.v2.{ App, Result }
import orion.common.service.MarathonClient.AppStatus

import scala.concurrent.{ ExecutionContext, Future }

trait ServiceBaseSpec extends ImplicitSystem
    with TestKitBase
    with BaseSpec
    with DefaultTimeout
    with ImplicitSender
    with MockFactory
    with DateSupportMocks
    with UUIDSupportMocks {

  trait ServiceScope extends FailureExpectationSupport with RabbitMqRpcClientSupportMocks with MarathonClientSupportMocks {
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

  trait RabbitMqRpcClientSupportMocks { self: ServiceScope =>

    val mockRabbitMqClient = mock[RabbitMqRpcClient]

    trait RabbitMqRpcClientSupportTesting extends RabbitMqRpcClientSupport { self: Service =>
      override val rabbitMqClient = mockRabbitMqClient
    }

    implicit class ExtendedMockRabbitMqClient(rabbitMqClient: RabbitMqRpcClient) {
      def sendMessageToExchangeExpects[T <: { val id: String }](message: T, exchangeName: String, routingKey: String)(implicit formats: Formats): CallHandler4[T, String, String, Formats, Unit] = {
        (rabbitMqClient.sendMessageToExchange[T](_: T, _: String, _: String)(_: Formats))
          .expects(message, exchangeName, routingKey, *)
      }

      def sendMessageToExchangeWithConfirmationExpects[T <: { val id: String }](message: T, exchangeName: String, routingKey: String): CallHandler6[T, String, String, ExecutionContext, Timeout, Formats, Future[Message.Ack]] = {
        (rabbitMqClient.sendMessageToExchangeWithConfirmation[T](_: T, _: String, _: String)(_: ExecutionContext, _: Timeout, _: Formats))
          .expects(message, exchangeName, routingKey, *, *, *)
      }

      def declareDirectBindingExpects(exchangeName: String, routingKey: String, queueName: String): CallHandler5[String, String, String, ExecutionContext, Timeout, Future[Unit]] = {
        (rabbitMqClient.declareDirectBinding(_: String, _: String, _: String)(_: ExecutionContext, _: Timeout))
          .expects(exchangeName, routingKey, queueName, *, *)
      }

      def deleteQueueExpects(queueName: String): CallHandler3[String, ExecutionContext, Timeout, Future[DeleteOk]] = {
        (rabbitMqClient.deleteQueue(_: String)(_: ExecutionContext, _: Timeout))
          .expects(queueName, *, *)
      }

      def subscribeExpects[T <: { val id: String }: Manifest](queueName: String): CallHandler6[String, (T => Unit), Manifest[T], ExecutionContext, Formats, RecoveryStrategy, SubscriptionRef] = {
        (rabbitMqClient.subscribe(_: String)(_: T => Unit)(_: Manifest[T], _: ExecutionContext, _: Formats, _: RecoveryStrategy))
          .expects(queueName, *, *, *, *, *)
      }
    }
  }

  trait MarathonClientSupportMocks { self: ServiceScope =>
    val mockMarathonClient = mock[MarathonClient]

    trait MarathonClientSupportTesting extends MarathonClientSupport { self: Service =>
      override val marathonClient = mockMarathonClient
    }

    implicit class ExtendedMarathonClient(marathonClient: MarathonClient) {
      def createAppExpects(expectedApp: App): CallHandler1[App, Future[App]] = {
        (marathonClient.createApp(_: App)).expects(where {
          (actualApp: App) => expectedApp.toString == actualApp.toString
        })
      }

      def destroyAppExpects(appId: String): CallHandler1[String, Future[Option[Result]]] = {
        (marathonClient.destroyApp(_: String))
          .expects(appId)
      }

      def getAppStatusExpects(appId: String): CallHandler1[String, Future[Option[AppStatus]]] = {
        (marathonClient.getAppStatus(_: String))
          .expects(appId)
      }
    }
  }

  trait FailureExpectationSupport { self: ServiceScope =>
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

trait DateSupportMocks extends DateImplicits { self: ServiceBaseSpec =>
  val mockCurrentDate: Date = new Date().withoutMillis()
}

trait UUIDSupportMocks { self: ServiceBaseSpec =>
  val mockRandomUUID: UUID = UUID.randomUUID()
}

trait ImplicitSystem { self: TestKitBase =>
  implicit val system = ActorSystem(systemName)

  private def systemName() = s"${this.getClass().getSimpleName()}-System"
}
