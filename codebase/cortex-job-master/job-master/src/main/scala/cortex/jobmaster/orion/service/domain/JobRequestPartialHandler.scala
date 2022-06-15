package cortex.jobmaster.orion.service.domain

import com.trueaccord.scalapb.GeneratedMessage
import cortex.api.job.JobRequest
import cortex.jobmaster.jobs.time.JobTimeInfo

import scala.concurrent.Future

trait JobRequestPartialHandler {
  import JobRequestPartialHandler._

  def handlePartial: PartialFunction[(JobId, JobRequest), JobResult]
}

object JobRequestPartialHandler {
  type JobResult = Future[(_ <: GeneratedMessage, JobTimeInfo)]
  type JobId = String
}
