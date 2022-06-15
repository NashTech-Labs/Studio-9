package baile.domain.dcproject

import java.time.Instant

case class Session(
  geminiSessionId: String,
  geminiSessionToken: String,
  geminiSessionUrl: String,
  dcProjectId: String,
  created: Instant
)
