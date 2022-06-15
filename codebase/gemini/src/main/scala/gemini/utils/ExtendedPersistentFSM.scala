package gemini.utils

import akka.persistence.fsm.{ LoggingPersistentFSM, PersistentFSM }
import akka.persistence.fsm.PersistentFSM.FSMState

trait ExtendedPersistentFSM[S <: FSMState, D, E] extends PersistentFSM[S, D, E] with LoggingPersistentFSM[S, D, E] {

  type ApplyEvent = PartialFunction[(E, D), D]

  def applyEventPF: ApplyEvent

  override def applyEvent(domainEvent: E, currentData: D): D =
    applyEventPF.lift((domainEvent, currentData)).getOrElse {
      log.debug("Unhandled event application in state {}/{}", domainEvent, currentData)
      currentData
    }

}
