package aries.domain.service.heartbeat

import java.util.UUID

import aries.domain.service.ServiceMessage

case class FindLatestHeartbeat(jobId: UUID) extends ServiceMessage