package baile.domain.process

import baile.domain.job.CortexJobStatus

sealed trait ProcessStatus

object ProcessStatus {

  case object Queued extends ProcessStatus
  case object Running extends ProcessStatus
  case object Completed extends ProcessStatus
  case object Cancelled extends ProcessStatus
  case object Failed extends ProcessStatus

  def apply(cortexJobStatus: CortexJobStatus): ProcessStatus = cortexJobStatus match {
    case CortexJobStatus.Submitted | CortexJobStatus.Queued => Queued
    case CortexJobStatus.Running => Running
    case CortexJobStatus.Cancelled => Cancelled
    case CortexJobStatus.Completed => Completed
    case CortexJobStatus.Failed => Failed
  }

}
