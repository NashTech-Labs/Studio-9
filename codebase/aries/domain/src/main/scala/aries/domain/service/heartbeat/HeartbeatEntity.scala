package aries.domain.service.heartbeat

import java.util.{ Date, UUID }

import aries.domain.service.{ DomainObject, UUIDEntity }

import scala.concurrent.duration.FiniteDuration

case class CreateHeartbeat(
  job_id:                   UUID,
  created_at:               Date,
  current_progress:         Double,
  estimated_time_remaining: Option[FiniteDuration]
) extends DomainObject

case class HeartbeatEntity(
  id:                       UUID,
  job_id:                   UUID,
  created_at:               Date,
  current_progress:         Double,
  estimated_time_remaining: Option[FiniteDuration]
) extends UUIDEntity

case class HeartbeatSearchCriteria(
  job_id: UUID
) extends DomainObject
