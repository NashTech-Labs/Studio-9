package baile.domain.job

sealed trait CortexJobStatus
sealed trait CortexJobTerminalStatus extends CortexJobStatus


object CortexJobStatus {
  case object Submitted extends CortexJobStatus
  case object Queued extends CortexJobStatus
  case object Running extends CortexJobStatus
  case object Completed extends CortexJobTerminalStatus
  case object Cancelled extends CortexJobTerminalStatus
  case object Failed extends CortexJobTerminalStatus
}
