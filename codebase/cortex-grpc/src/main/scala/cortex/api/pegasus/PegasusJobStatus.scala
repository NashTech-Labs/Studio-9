package cortex.api.pegasus

sealed trait PegasusJobStatus

object PegasusJobStatus {
  case object Succeed extends PegasusJobStatus
  case object Failed extends PegasusJobStatus
}
