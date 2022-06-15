package baile.routes.contract

import baile.domain.dcproject.{ DCProjectStatus, SessionStatus }
import baile.utils.json.EnumWritesBuilder
import play.api.libs.json.Writes

package object dcproject {

  implicit val DCProjectStatusWrites: Writes[DCProjectStatus] = EnumWritesBuilder.build {
    case DCProjectStatus.Idle => "IDLE"
    case DCProjectStatus.Interactive => "INTERACTIVE"
    case DCProjectStatus.Building => "BUILDING"
  }

  implicit val SessionStatusWrites: Writes[SessionStatus] = EnumWritesBuilder.build {
    case SessionStatus.Running => "RUNNING"
    case SessionStatus.Completed => "COMPLETED"
    case SessionStatus.Queued => "QUEUED"
    case SessionStatus.Submitted => "SUBMITTED"
    case SessionStatus.Failed => "FAILED"
  }

}
