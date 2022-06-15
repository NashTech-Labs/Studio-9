package orion.ipc.rabbitmq

import akka.actor.{ Actor, ActorLogging }
import akka.util.Timeout
import com.rabbitmq.client.AMQP.Queue.DeleteOk
import com.spingo.op_rabbit.Message.Ack
import com.spingo.op_rabbit._
import org.json4s.Formats

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }

trait RabbitMqRpcClientSupport { this: Actor with ActorLogging =>

  val rabbitMqClient = RabbitMqRpcClient(context.system)
  implicit def recoveryStrategy = rabbitMqClient.defaultRecoveryStrategy
  private var subscriptions = Vector.empty[SubscriptionRef]

  def sendMessageToExchange[T <: { val id: String }](message: T, exchangeName: String, routingKey: String)(implicit formats: Formats): Unit = {
    rabbitMqClient.sendMessageToExchange(message, exchangeName, routingKey)
  }

  def sendMessageToExchangeWithConfirmation[T <: { val id: String }](message: T, exchangeName: String, routingKey: String)(implicit ec: ExecutionContext, timeout: Timeout, formats: Formats): Future[Ack] = {
    rabbitMqClient.sendMessageToExchangeWithConfirmation(message, exchangeName, routingKey)
  }

  def declareDirectBinding(exchangeName: String, routingKey: String, queueName: String)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {
    rabbitMqClient.declareDirectBinding(exchangeName, routingKey, queueName)
  }

  def deleteQueue(queueName: String)(implicit ec: ExecutionContext, timeout: Timeout): Future[DeleteOk] = {
    rabbitMqClient.deleteQueue(queueName)
  }

  def subscribe[T <: { val id: String }: Manifest](queueName: String, throughput: Int, backoff: Option[FiniteDuration])(implicit executionContext: ExecutionContext, formats: Formats, recoveryStrategy: RecoveryStrategy): SubscriptionRef = {
    // NOTE: this way message subscribing and handling will be analogous to the
    // way it's being done with Akka EventBus: http://doc.akka.io/docs/akka/2.5.3/scala/event-bus.html
    val subscription = rabbitMqClient.subscribe[T](queueName, throughput, backoff)(msg => self ! msg)
    subscriptions = subscriptions :+ subscription
    subscription
  }

  def subscribe[T <: { val id: String }: Manifest](queueName: String)(implicit executionContext: ExecutionContext, formats: Formats, recoveryStrategy: RecoveryStrategy): SubscriptionRef = {
    val subscription = rabbitMqClient.subscribe[T](queueName)(msg => self ! msg)
    subscriptions = subscriptions :+ subscription
    subscription
  }

  override def postStop(): Unit = {
    subscriptions.foreach(_.close())
  }

}