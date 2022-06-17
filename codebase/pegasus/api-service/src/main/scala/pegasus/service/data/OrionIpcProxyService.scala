package pegasus.service.data

import akka.actor.{ ActorRef, Props }
import cortex.api.pegasus.{ PredictionImportRequest, PredictionImportResponse }
import pegasus.common.orionipc.OrionIpcProvider
import pegasus.common.service.{ NamedActor, Service }

object OrionIpcProxyService extends NamedActor {
  override val Name = "orion-ipc-service"
  def props(dataCommandService: ActorRef, orionIpcProvider: OrionIpcProvider): Props = {
    Props(new OrionIpcProxyService(dataCommandService, orionIpcProvider))
  }
}

class OrionIpcProxyService(dataCommandService: ActorRef, orionIpcProvider: OrionIpcProvider) extends Service {
  implicit val dispatcher = context.dispatcher

  def handle(predictionImportRequest: PredictionImportRequest): Unit = {
    dataCommandService ! predictionImportRequest
  }

  override def preStart(): Unit = {
    orionIpcProvider.subscribe(handle)
  }

  override def receive: Receive = {
    case p: PredictionImportResponse => orionIpcProvider.respondToExchange(p)
  }
}
