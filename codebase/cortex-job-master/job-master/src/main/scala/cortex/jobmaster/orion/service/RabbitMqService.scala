package cortex.jobmaster.orion.service

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.spingo.op_rabbit.{ RecoveryStrategy, SubscriptionRef }
import cortex.api.job.message.JobMessage
import cortex.jobmaster.common.json4s.CortexJson4sSupport
import orion.ipc.rabbitmq.RabbitMqRpcClient

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ Await, ExecutionContext }

class RabbitMqService(actorSystem: ActorSystem) extends CortexJson4sSupport {
  private lazy val mqRpcClient = RabbitMqRpcClient(actorSystem)

  // RabbitMqRpcClient requires to have ExecutionContext and RecoveryStrategy implicits provided
  private implicit lazy val ec: ExecutionContext = actorSystem.dispatcher
  private implicit val recoveryStrategy: AnyRef with RecoveryStrategy = RecoveryStrategy.limitedRedeliver()
  private var jobSubscription: SubscriptionRef = _

  def subscribe[T <: JobMessage: Manifest](queue: String, handle: T => Unit): Unit = {
    jobSubscription = mqRpcClient.subscribe[T](queue) {
      handle
    }
  }

  def sendMessageToExchange[T <: JobMessage](message: T, exchangeName: String, routingKey: String): Unit = {
    mqRpcClient.sendMessageToExchange[T](message, exchangeName, routingKey)
  }

  def stop(): Unit = {
    jobSubscription.close(FiniteDuration(1, TimeUnit.MINUTES))
    Await.result(jobSubscription.closed, FiniteDuration(1, TimeUnit.MINUTES))
  }
}
