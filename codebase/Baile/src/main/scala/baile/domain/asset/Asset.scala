package baile.domain.asset

import java.time.Instant
import java.util.UUID

trait Asset[S <: AssetStatus] {
  val ownerId: UUID
  val name: String
  val status: S
  val created: Instant
  val updated: Instant
  val inLibrary: Boolean
  val description: Option[String]
}
