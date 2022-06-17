package taurus.domain.rest.job

import java.util.UUID

import taurus.domain.rest.HttpContract

case class SubmitJobDataContract(
  id:        Option[UUID],
  owner:     UUID,
  jobType:   String,
  inputPath: String
) extends HttpContract
