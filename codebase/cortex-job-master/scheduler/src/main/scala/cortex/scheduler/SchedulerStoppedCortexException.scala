package cortex.scheduler

import cortex.CortexException

case class SchedulerStoppedCortexException() extends CortexException("Scheduler was stopped")
