package baile.domain.job

import java.time.Instant

case class CortexTimeInfo(
  submittedAt: Instant,
  startedAt: Instant,
  completedAt: Instant
)
