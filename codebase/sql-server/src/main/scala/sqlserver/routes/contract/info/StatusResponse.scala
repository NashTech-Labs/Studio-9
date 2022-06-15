package sqlserver.routes.contract.info

import sqlserver.domain.info.Status
import play.api.libs.json.{ Json, OWrites }
import sqlserver.utils.json.CommonFormats.DurationFormat

import scala.concurrent.duration.Duration

case class StatusResponse(uptime: Duration)

object StatusResponse {

  implicit val StatusResponseWrites: OWrites[StatusResponse] = Json.writes[StatusResponse]

  def fromDomain(status: Status): StatusResponse = StatusResponse(
    uptime = status.uptime
  )

}
