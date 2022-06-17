package taurus.pegasus

import akka.actor.Props
import cortex.api.pegasus.{ PredictionImportRequest, PredictionImportResponse }
import taurus.common.service.Service

class PegasusService(orionIpcProvider: OrionIpcProvider) extends Service with PegasusServiceState {
  implicit val dispatcher = context.dispatcher

  protected def handler(predictionImportResponse: PredictionImportResponse): Unit = {
    getSenderRef(predictionImportResponse.jobId).foreach(ref => {
      ref ! predictionImportResponse
      removeSenderRef(predictionImportResponse.jobId)
    })
  }

  override def preStart(): Unit = {
    orionIpcProvider.subscribe(handler)
  }

  override def receive: Receive = {
    case req: PredictionImportRequest =>
      // here we assume that there will be the only one request per job
      saveSenderRef(req.jobId, sender)
      orionIpcProvider.sendRequestToExchange(req)
  }
}

object PegasusService {
  // should be initialized once in application
  def props(orionIpcProvider: OrionIpcProvider): Props = {
    Props(new PegasusService(orionIpcProvider))
  }
}
