package orion.ipc.rabbitmq

import akka.actor.{ ActorRef, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props }
import akka.event.{ Logging, LoggingAdapter }
import akka.pattern.ask
import akka.util.Timeout
import com.newmotion.akka.rabbitmq.ConnectionActor.ProvideChannel
import com.rabbitmq.client.AMQP.Queue.DeleteOk
import com.rabbitmq.client.Channel
import com.spingo.op_rabbit.Directives.queue
import com.spingo.op_rabbit.Message.{ Ack, ConfirmResponse }
import com.spingo.op_rabbit._
import org.json4s.Formats
import orion.ipc.common._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

object RabbitMqRpcClient extends ExtensionId[RabbitMqRpcClient] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): RabbitMqRpcClient = new RabbitMqRpcClient {
    override val rabbitControl = system.actorOf(Props(new RabbitControl), "rabbit-control")
    override val defaultRecoveryStrategy = RecoveryStrategy.limitedRedeliver()
    override val logger = Logging(system, getClass)
  }

  override def lookup(): ExtensionId[_ <: Extension] = RabbitMqRpcClient
}

trait RabbitMqRpcClient extends Extension {

  import Json4sSupport.jackson._

  protected val rabbitControl: ActorRef
  protected val logger: LoggingAdapter

  val defaultRecoveryStrategy: RecoveryStrategy

  def sendMessageToExchange[T <: { val id: String }](message: T, exchangeName: String, routingKey: String)(implicit formats: Formats): Unit = {
    rabbitControl ! Message.topic(message, routingKey, exchangeName)
    logger.debug("Sent message with ID [{}] to exchange [{}] with routing key {}", message.id, exchangeName, routingKey)
  }

  def sendMessageToExchangeWithConfirmation[T <: { val id: String }](message: T, exchangeName: String, routingKey: String)(implicit ec: ExecutionContext, timeout: Timeout, formats: Formats): Future[Ack] = {

    (rabbitControl ? Message.topic(message, routingKey, exchangeName)).mapTo[ConfirmResponse] flatMap {
      case s @ Message.Ack(_) =>
        logger.debug("Sent message with ID [{}] to exchange [{}] with routing key {}", message.id, exchangeName, routingKey)
        Future.successful(s)
      case _ => Future.failed(new Exception(s"Cannot create the queue: ${message.id}"))
    }
  }

  def declareDirectBinding(exchangeName: String, routingKey: String, queueName: String)(implicit ec: ExecutionContext, timeout: Timeout): Future[Unit] = {
    val directBinding =
      Binding.direct(
        queue(queueName, durable = true, exclusive = false, autoDelete = false),
        Exchange.passive(exchangeName),
        List(routingKey)
      )

    def declareBinding(channel: Channel): Future[Unit] = {
      Future(directBinding.declare(channel)).closeWhenDone(channel)
    }

    for {
      connectionActor <- (rabbitControl ? RabbitControl.GetConnectionActor).mapTo[ActorRef]
      channel <- (connectionActor ? ProvideChannel).mapTo[Channel]
      result <- declareBinding(channel)
    } yield result
  }

  def deleteQueue(queueName: String)(implicit ec: ExecutionContext, timeout: Timeout): Future[DeleteOk] = {
    def queueDelete(channel: Channel): Future[DeleteOk] = {
      Future(channel.queueDelete(queueName)).closeWhenDone(channel)
    }

    for {
      connectionActor <- (rabbitControl ? RabbitControl.GetConnectionActor).mapTo[ActorRef]
      channel <- (connectionActor ? ProvideChannel).mapTo[Channel]
      result <- queueDelete(channel)
    } yield result
  }

  def subscribe[T <: { val id: String }: Manifest](queueName: String)(handler: T => Unit)(implicit executionContext: ExecutionContext, formats: Formats, recoveryStrategy: RecoveryStrategy): SubscriptionRef = {
    subscribe[T](queueName, 1, None)(handler)
  }

  def subscribe[T <: { val id: String }: Manifest](queueName: String, throughput: Int, backoff: Option[FiniteDuration])(handler: T => Unit)(implicit executionContext: ExecutionContext, formats: Formats, recoveryStrategy: RecoveryStrategy): SubscriptionRef = {

    Subscription.run(rabbitControl) {
      import Directives._
      channel(qos = throughput) {
        consume(Queue.passive(queueName)) {
          body(as[T]) { message =>
            logger.debug("Received message with ID [{}] from [{}] queue", message.id, queueName)
            val result =
              Future(handler(message))
                .andThen {
                  case Success(_) => logger.debug("Successfully processed message with ID [{}] from [{}] queue", message.id, queueName)
                  case Failure(e) => logger.debug("Error while processing message with ID [{}] from [{}] queue [{}]", message.id, queueName, e)
                }

            val resultWithBackoff =
              backoff.fold(result) { interval =>
                result andThen {
                  case _ => Thread.sleep(interval.toMillis)
                }
              }

            ack(resultWithBackoff)
          }
        }
      }
    }
  }
}
