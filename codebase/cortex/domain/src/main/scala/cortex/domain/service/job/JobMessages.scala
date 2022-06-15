package cortex.domain.service.job

import java.util.{ Date, UUID }

import cortex.domain.service.ServiceMessage

case class FindJob(criteria: JobSearchCriteria) extends ServiceMessage
case class GetJobStatus(jobId: UUID) extends ServiceMessage
