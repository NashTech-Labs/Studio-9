package cortex.jobmaster.orion.service.domain

import cortex.api.job.JobRequest
import cortex.jobmaster.orion.service.domain.JobRequestPartialHandler.{ JobId, JobResult }

trait JobRequestHandler {
  def handleJobRequest(jobRequest: (JobId, JobRequest)): JobResult
}
