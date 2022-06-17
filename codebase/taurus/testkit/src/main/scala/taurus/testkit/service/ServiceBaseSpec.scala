package taurus.testkit.service

import java.util
import java.util.{ Date, UUID }

import akka.actor.{ ActorRef, ActorSystem, Status }
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.HttpCredentials
import akka.stream.ActorMaterializer
import akka.testkit.{ CallingThreadDispatcher, DefaultTimeout, ImplicitSender, TestKit, TestKitBase }
import akka.util.Timeout
import com.rabbitmq.client.AMQP.Queue.DeleteOk
import com.spingo.op_rabbit.{ Message, RecoveryStrategy, SubscriptionRef }
import org.json4s.{ Formats, Serialization }
import org.scalamock.handlers.{ CallHandler6, _ }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, Suite }
import orion.ipc.rabbitmq.{ RabbitMqRpcClient, RabbitMqRpcClientSupport }
import taurus.common.service.RemoteRepository.WriteResult
import taurus.common.service._
import taurus.testkit.BaseSpec

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

trait ServiceBaseSpec extends ImplicitSystem
    with TestKitBase
    with BaseSpec
    with DefaultTimeout
    with ImplicitSender
    with StopSystemAfterAll
    with MockFactory
    with DateSupportMocks
    with UUIDSupportMocks {

  trait ServiceScope extends FailureExpectationSupport
      with HttpClientSupportMocks
      with RabbitMqRpcClientSupportMocks
      with RemoteRepositorySupportMocks {

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

  trait HttpClientSupportMocks { self: ServiceScope =>

    val mockHttpClient = mock[HttpClient]
    val mockMaterializer = ActorMaterializer()

    (mockHttpClient.getMaterializer _).expects().returning(mockMaterializer).anyNumberOfTimes()

    trait HttpClientSupportTesting extends HttpClientSupport { self: Service =>
      override val httpClient = mockHttpClient
    }

    implicit class ExtendedMockHttpClient(httpClient: HttpClient) {
      def postExpects[T <: AnyRef](uri: String, entity: T, credentials: Option[HttpCredentials] = None): CallHandler5[String, T, Option[HttpCredentials], Serialization, Formats, Future[HttpResponse]] = {
        (httpClient.post[T](_: String, _: T, _: Option[HttpCredentials])(_: Serialization, _: Formats))
          .expects(uri, entity, credentials, *, *)
      }

      def getExpects(uri: String, credentials: Option[HttpCredentials] = None): CallHandler2[String, Option[HttpCredentials], Future[HttpResponse]] = {
        (httpClient.get(_, _))
          .expects(uri, credentials)
      }

      def putExpects[T <: AnyRef](uri: String, entity: T, credentials: Option[HttpCredentials] = None): CallHandler5[String, T, Option[HttpCredentials], Serialization, Formats, Future[HttpResponse]] = {
        (httpClient.put[T](_: String, _: T, _: Option[HttpCredentials])(_: Serialization, _: Formats))
          .expects(uri, entity, credentials, *, *)
      }

      def deleteExpects(uri: String, credentials: Option[HttpCredentials] = None): CallHandler2[String, Option[HttpCredentials], Future[HttpResponse]] = {
        (httpClient.delete(_, _))
          .expects(uri, credentials)
      }
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

      def subscribeExpects[T <: { val id: String }: Manifest](queueName: String, throughput: Int, backoff: Option[FiniteDuration]): CallHandler8[String, Int, Option[FiniteDuration], (T => Unit), Manifest[T], ExecutionContext, Formats, RecoveryStrategy, SubscriptionRef] = {
        (rabbitMqClient.subscribe(_: String, _: Int, _: Option[FiniteDuration])(_: T => Unit)(_: Manifest[T], _: ExecutionContext, _: Formats, _: RecoveryStrategy))
          .expects(queueName, throughput, backoff, *, *, *, *, *)
      }
    }
  }

  trait RemoteRepositorySupportMocks { self: ServiceScope =>

    val mockRemoteRepository = mock[RemoteRepository]

    trait RemoteRepositorySupportTesting extends RemoteRepositorySupport { self: Service =>
      override val remoteRepository = mockRemoteRepository
    }

    implicit class ExtendedMockRemoteRepository(remoteRepository: RemoteRepository) {
      def writeExpects(path: String, content: Array[Byte]): CallHandler2[String, Array[Byte], Future[WriteResult]] = {
        (remoteRepository.write(_, _)).expects(path, argThat[Array[Byte]](util.Arrays.equals(content, _)))
      }

      def readExpects(path: String): CallHandler1[String, Future[Array[Byte]]] = {
        (remoteRepository.read(_)).expects(path)
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

trait StopSystemAfterAll extends BeforeAndAfterAll { self: TestKitBase with Suite =>
  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
