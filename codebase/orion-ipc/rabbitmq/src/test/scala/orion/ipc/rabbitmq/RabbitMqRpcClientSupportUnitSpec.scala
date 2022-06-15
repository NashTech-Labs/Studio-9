package orion.ipc.rabbitmq

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props, Status }
import akka.testkit.{ DefaultTimeout, ImplicitSender, TestActorRef, TestKit }
import akka.util.Timeout
import com.rabbitmq.client.impl.AMQImpl
import com.spingo.op_rabbit.Message.Ack
import com.spingo.op_rabbit.{ RecoveryStrategy, SubscriptionRef }
import org.json4s.{ DefaultFormats, Formats }
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import orion.ipc.testkit.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

object RabbitMqRpcClientSupportUnitSpec {

  object TestActor {

    case object SendMessageToExchange
    case object SendMessageToExchangeWithConfirmation
    case object DeclareDirectBinding
    case object DeleteQueue
    case object Subscribe

    case class TestMessage(id: String)

    val Message = TestMessage("id")
    val ExchangeName = "exchange-name"
    val RoutingKey = "routing-key"
    val QueueName = "queue-name"

  }

  class TestActor extends Actor with ActorLogging with RabbitMqRpcClientSupport {
    import TestActor._
    import akka.pattern.pipe

    implicit val ec = context.dispatcher
    implicit val timeout = Timeout(5 seconds)
    implicit val formats = DefaultFormats

    override def receive: Receive = {

      case SendMessageToExchange                 => sendMessageToExchange(Message, ExchangeName, RoutingKey)

      case SendMessageToExchangeWithConfirmation => sendMessageToExchangeWithConfirmation(Message, ExchangeName, RoutingKey) pipeTo sender

      case DeclareDirectBinding                  => declareDirectBinding(ExchangeName, RoutingKey, QueueName) pipeTo sender

      case DeleteQueue                           => deleteQueue(QueueName) pipeTo sender

      case Subscribe                             => sender ! subscribe[TestMessage](QueueName)

    }
  }

}

class RabbitMqRpcClientSupportUnitSpec extends TestKit(ActorSystem("RabbitMqRpcClientSupportUnitSpec"))
    with BaseSpec
    with DefaultTimeout
    with ImplicitSender
    with MockFactory
    with BeforeAndAfterAll {

  import RabbitMqRpcClientSupportUnitSpec._
  import RabbitMqRpcClientSupportUnitSpec.TestActor._

  trait Scope {
    val mockRabbitMqClient = mock[RabbitMqRpcClient]

    val testActor = TestActorRef(Props(new TestActor() {
      override val rabbitMqClient = mockRabbitMqClient
    }))
  }

  "When sending a SendMessageToExchange msg to the TestActor, it" should {
    "call the corresponding method in the RabbitMq client api and do not respond anything if no errors" in new Scope {
      (mockRabbitMqClient.sendMessageToExchange[TestMessage](_: TestMessage, _: String, _: String)(_: Formats))
        .expects(Message, ExchangeName, RoutingKey, *)
        .returning(())

      testActor ! SendMessageToExchange
    }
    // NOTE: we might change following behaviour and wrap the call with a Try so that the exception does not get
    // propagated and the Actor terminated.
    "call the corresponding method in the RabbitMq client api and do not respond anything if there's an error" in new Scope {
      val unexpectedException = new Exception("BOOM!")
      (mockRabbitMqClient.sendMessageToExchange[TestMessage](_: TestMessage, _: String, _: String)(_: Formats))
        .expects(Message, ExchangeName, RoutingKey, *)
        .throwing(unexpectedException)

      testActor ! SendMessageToExchange
    }
  }

  "When sending a SendMessageToExchangeWithConfirmation msg to the TestActor, it" should {
    "call the corresponding method in the RabbitMq client api and respond with an Ack msg if no errors" in new Scope {
      val result = Ack(1L)
      (mockRabbitMqClient.sendMessageToExchangeWithConfirmation[TestMessage](_: TestMessage, _: String, _: String)(_: ExecutionContext, _: Timeout, _: Formats))
        .expects(Message, ExchangeName, RoutingKey, *, *, *)
        .returning(Future.successful(result))

      testActor ! SendMessageToExchangeWithConfirmation

      expectMsg(result)
    }
    "call the corresponding method in the RabbitMq client api and respond with a Failure msg if there's an error" in new Scope {
      val unexpectedException = new Exception("BOOM!")
      (mockRabbitMqClient.sendMessageToExchangeWithConfirmation[TestMessage](_: TestMessage, _: String, _: String)(_: ExecutionContext, _: Timeout, _: Formats))
        .expects(Message, ExchangeName, RoutingKey, *, *, *)
        .returning(Future.failed(unexpectedException))

      testActor ! SendMessageToExchangeWithConfirmation

      expectMsg(Status.Failure(unexpectedException))
    }
  }

  "When sending a DeclareDirectBinding msg to the TestActor, it" should {
    "call the corresponding method in the RabbitMq client api and respond with a Unit msg if no errors" in new Scope {
      val result = ()
      (mockRabbitMqClient.declareDirectBinding(_: String, _: String, _: String)(_: ExecutionContext, _: Timeout))
        .expects(ExchangeName, RoutingKey, QueueName, *, *)
        .returning(Future.successful(result))

      testActor ! DeclareDirectBinding

      expectMsg(result)
    }
    "call the corresponding method in the RabbitMq client api and respond with a Failure msg if there's an error" in new Scope {
      val unexpectedException = new Exception("BOOM!")
      (mockRabbitMqClient.declareDirectBinding(_: String, _: String, _: String)(_: ExecutionContext, _: Timeout))
        .expects(ExchangeName, RoutingKey, QueueName, *, *)
        .returning(Future.failed(unexpectedException))

      testActor ! DeclareDirectBinding

      expectMsg(Status.Failure(unexpectedException))
    }
  }

  "When sending a DeleteQueue msg to the TestActor, it" should {
    "call the corresponding method in the RabbitMq client api and respond with a DeleteOk msg if no errors" in new Scope {
      val result = new AMQImpl.Queue.DeleteOk(1)
      (mockRabbitMqClient.deleteQueue(_: String)(_: ExecutionContext, _: Timeout))
        .expects(QueueName, *, *)
        .returning(Future.successful(result))

      testActor ! DeleteQueue

      expectMsg(result)
    }
    "call the corresponding method in the RabbitMq client api and respond with a Failure msg if there's an error" in new Scope {
      val unexpectedException = new Exception("BOOM!")
      (mockRabbitMqClient.deleteQueue(_: String)(_: ExecutionContext, _: Timeout))
        .expects(QueueName, *, *)
        .returning(Future.failed(unexpectedException))

      testActor ! DeleteQueue

      expectMsg(Status.Failure(unexpectedException))
    }
  }

  "When sending a Subscribe msg to the TestActor, it" should {
    "call the corresponding method in the RabbitMq client api and respond with a SubscriptionRef msg if no errors" in new Scope {
      val mockSubscriptionRef = mock[SubscriptionRef]
      (mockRabbitMqClient.subscribe(_: String)(_: TestMessage => Unit)(_: Manifest[TestMessage], _: ExecutionContext, _: Formats, _: RecoveryStrategy))
        .expects(QueueName, *, *, *, *, *)
        .returning(mockSubscriptionRef)

      testActor ! Subscribe
      expectMsg(mockSubscriptionRef)
    }
    // NOTE: we might change following behaviour and wrap the call with a Try so that the exception does not get
    // propagated and the Actor terminated.
    "call the corresponding method in the RabbitMq client api and do not respond anything if there's an error" in new Scope {
      val unexpectedException = new Exception("BOOM!")
      (mockRabbitMqClient.subscribe(_: String)(_: TestMessage => Unit)(_: Manifest[TestMessage], _: ExecutionContext, _: Formats, _: RecoveryStrategy))
        .expects(QueueName, *, *, *, *, *)
        .throwing(unexpectedException)

      testActor ! Subscribe
    }
    "close the SubscriptionRef channel when the Actor stops if no errors" in new Scope {
      val mockSubscriptionRef = mock[SubscriptionRef]
      (mockRabbitMqClient.subscribe(_: String)(_: TestMessage => Unit)(_: Manifest[TestMessage], _: ExecutionContext, _: Formats, _: RecoveryStrategy))
        .expects(QueueName, *, *, *, *, *)
        .returning(mockSubscriptionRef)

      testActor ! Subscribe
      expectMsg(mockSubscriptionRef)

      watch(testActor)
      (mockSubscriptionRef.close _).expects(*).once()
      system.stop(testActor)
      expectTerminated(testActor)
    }
  }

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}

