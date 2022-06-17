package cortex.jobmaster.jobs.job

import cortex.task.common.ClassReference
import cortex.api.job.common.{ ClassReference => CortexClassReference }

object CommonConverters {

  def fromCortexClassReference(cortexClassReference: CortexClassReference): ClassReference = {
    ClassReference(
      packageLocation = cortexClassReference.packageLocation,
      moduleName      = cortexClassReference.moduleName,
      className       = cortexClassReference.className
    )
  }
}
