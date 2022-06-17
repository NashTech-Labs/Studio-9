package baile.services.images

import baile.services.cortex.job.CortexJobService.CortexJobServiceError

case class UploadJobSubmissionFailedException (
  error: CortexJobServiceError
) extends RuntimeException(
  s"Error starting upload job: '$error'"
)
