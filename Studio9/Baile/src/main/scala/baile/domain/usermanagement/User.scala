package baile.domain.usermanagement

import java.time.Instant
import java.util.UUID

sealed trait User {
  val id: UUID
  val username: String
  val email: String
  val firstName: String
  val lastName: String
  val permissions: Seq[Permission]
  val role: Role
}

case class RegularUser(
  id: UUID,
  username: String,
  email: String,
  firstName: String,
  lastName: String,
  status: UserStatus,
  created: Instant,
  updated: Instant,
  permissions: Seq[Permission],
  role: Role
) extends User {

  def toExperimentExecutor(experimentId: String): ExperimentExecutor =
    ExperimentExecutor(id, username, email, firstName, lastName, experimentId, permissions, role)

}

case class ExperimentExecutor(
  id: UUID,
  username: String,
  email: String,
  firstName: String,
  lastName: String,
  experimentId: String,
  permissions: Seq[Permission],
  role: Role
) extends User
