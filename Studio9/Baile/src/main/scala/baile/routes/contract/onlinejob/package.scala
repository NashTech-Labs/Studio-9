package baile.routes.contract

import baile.domain.onlinejob.OnlineJobStatus
import baile.utils.json.EnumWritesBuilder
import play.api.libs.json._

package object onlinejob {

  implicit val OnlineJobStatusWrites: Writes[OnlineJobStatus] = EnumWritesBuilder.build {
    case OnlineJobStatus.Running => "RUNNING"
    case OnlineJobStatus.Idle => "IDLE"
  }

}
