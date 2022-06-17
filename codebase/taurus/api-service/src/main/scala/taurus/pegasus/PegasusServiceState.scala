package taurus.pegasus

import akka.actor.ActorRef

import scala.collection.immutable.Map

trait PegasusServiceState {
  private var jobIdToSender = Map[String, ActorRef]()

  protected def saveSenderRef(jobId: String, sender: ActorRef): Unit = {
    jobIdToSender += (jobId -> sender)
  }

  protected def getSenderRef(jobId: String): Option[ActorRef] = {
    jobIdToSender.get(jobId)
  }

  protected def removeSenderRef(jobId: String): Unit = {
    jobIdToSender -= jobId
  }
}
