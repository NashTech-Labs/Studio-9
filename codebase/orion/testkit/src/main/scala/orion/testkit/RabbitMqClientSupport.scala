package orion.testkit

import akka.actor.Props
import akka.event.Logging
import akka.testkit.TestKitBase
import akka.util.Timeout
import com.spingo.op_rabbit.Message.Ack
import com.spingo.op_rabbit.{ RabbitControl, RecoveryStrategy, SubscriptionRef }
import org.json4s.Formats
import orion.ipc.rabbitmq.RabbitMqRpcClient

import scala.concurrent.{ ExecutionContext, Future, Promise }

trait RabbitMqClientSupport { self: TestKitBase =>

  // NOTE: do not use Akka extension here so that the Client is not shared
  // between the tested code and the test itself
  val rabbitMqClient = new RabbitMqRpcClient {
    override val rabbitControl = system.actorOf(Props(new RabbitControl), "rabbit-control-test")
    override val defaultRecoveryStrategy = RecoveryStrategy.limitedRedeliver()
    override val logger = Logging(system, getClass)
  }

  def subscribe[T <: { val id: String }: Manifest](queueName: String)(implicit executionContext: ExecutionContext, formats: Formats, recoveryStrategy: RecoveryStrategy = rabbitMqClient.defaultRecoveryStrategy): SubscriptionRef = {
    rabbitMqClient.subscribe[T](queueName)(msg => testActor ! msg)
  }

  def sendMessage[T <: { val id: String }](message: T, exchangeName: String, routingKey: String)(implicit ec: ExecutionContext, timeout: Timeout, formats: Formats): Future[Ack] = {
    rabbitMqClient.sendMessageToExchangeWithConfirmation(message, exchangeName, routingKey)
  }

  def getMessage[T <: { val id: String }: Manifest](queueName: String)(implicit executionContext: ExecutionContext, formats: Formats, recoveryStrategy: RecoveryStrategy = rabbitMqClient.defaultRecoveryStrategy): Future[T] = {
    val p = Promise[T]()
    val sub = rabbitMqClient.subscribe[T](queueName)(msg => p.success(msg))
    p.future andThen {
      case _ => sub.close()
    }
  }
}
