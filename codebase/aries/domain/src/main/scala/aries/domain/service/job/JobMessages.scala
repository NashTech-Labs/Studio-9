package aries.domain.service.job

import aries.domain.service.ServiceMessage

case class FindJob(criteria: JobSearchCriteria) extends ServiceMessage
