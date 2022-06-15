package gemini.domain.jupyter

import java.time.Instant
import java.util.UUID

case class Session(
  id: UUID,
  token: String,
  url: String,
  status: SessionStatus,
  startedAt: Instant
)
