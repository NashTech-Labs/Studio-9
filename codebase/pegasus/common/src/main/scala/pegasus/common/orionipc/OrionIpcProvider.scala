package pegasus.common.orionipc

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

  def respondToExchange(msg: PredictionImportResponse): Unit = {
    val routingKey = MlJobTopology.PegasusOutRoutingKey.format(msg.jobId)
    mqRpcClient.sendMessageToExchange(msg, MlJobTopology.GatewayExchange, routingKey)(formats)
  }

  def subscribe(handler: PredictionImportRequest => Unit)(implicit executionContext: ExecutionContext): SubscriptionRef = {
    mqRpcClient.subscribe[PredictionImportRequest](MlJobTopology.PegasusInQueue)(handler)
  }
}

object OrionIpcProvider {
  def apply(actorSystem: ActorSystem)(implicit executionContext: ExecutionContext): OrionIpcProvider = {
    new OrionIpcProvider(actorSystem)
  }
}
