package taurus.pegasus

import akka.actor.ActorSystem
import com.spingo.op_rabbit.{ RecoveryStrategy, SubscriptionRef }
import cortex.api.BaseJson4sFormats
import cortex.api.pegasus.{ PredictionImportRequest, PredictionImportResponse }
import orion.ipc.rabbitmq.{ MlJobTopology, RabbitMqRpcClient }

import scala.concurrent.ExecutionContext

class OrionIpcProvider(actorSystem: ActorSystem)(implicit executionContext: ExecutionContext) {
  lazy val mqRpcClient = RabbitMqRpcClient(actorSystem)
  implicit val formats = BaseJson4sFormats.extend(
    PredictionImportRequest.CreatedByFormats,
    PredictionImportResponse.PegasusJobStatusFormats
  )
  implicit val recoveryStrategy = RecoveryStrategy.limitedRedeliver()

  def sendRequestToExchange(msg: PredictionImportRequest): Unit = {
    val routingKey = MlJobTopology.PegasusInRoutingKey.format(msg.jobId)
    mqRpcClient.sendMessageToExchange(msg, MlJobTopology.GatewayExchange, routingKey)(formats)
  }

  def subscribe(handler: PredictionImportResponse => Unit)(implicit executionContext: ExecutionContext): SubscriptionRef = {
    mqRpcClient.subscribe[PredictionImportResponse](MlJobTopology.PegasusOutQueue)(handler)
  }
}

object OrionIpcProvider {
  def apply(actorSystem: ActorSystem)(implicit executionContext: ExecutionContext): OrionIpcProvider = {
    new OrionIpcProvider(actorSystem)
  }
}
