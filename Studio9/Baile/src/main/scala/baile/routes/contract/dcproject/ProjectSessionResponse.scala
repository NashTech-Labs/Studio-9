package baile.routes.contract.dcproject

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.dcproject.Session
import play.api.libs.json.{ Json, OWrites }

case class ProjectSessionResponse(
  id: String,
  authToken: String,
  url: String,
  dcProjectId: String,
  created: Instant
)

object ProjectSessionResponse {
  implicit val ProjectSessionResponseWrites: OWrites[ProjectSessionResponse] = Json.writes[ProjectSessionResponse]

  def fromDomain(sessionWithId: WithId[Session]): ProjectSessionResponse = sessionWithId match {
    case WithId(session, _) => ProjectSessionResponse(
      id = session.geminiSessionId,
      authToken = session.geminiSessionToken,
      url = session.geminiSessionUrl,
      dcProjectId = session.dcProjectId,
      created = session.created
    )
  }
}
