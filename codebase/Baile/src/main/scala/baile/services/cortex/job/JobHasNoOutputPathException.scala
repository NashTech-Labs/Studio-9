package baile.services.cortex.job

import java.util.UUID

case class JobHasNoOutputPathException (
  jobId: UUID
) extends RuntimeException(
  s"Job $jobId has no output path defined"
)

