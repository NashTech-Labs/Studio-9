package baile.routes.contract.info

import play.api.libs.json.{ Json, OWrites }
import baile.utils.json.CommonFormats.DurationFormat

import scala.concurrent.duration.Duration

case class StatusResponse(uptime: Duration)

object StatusResponse {

  implicit val StatusResponseWrites: OWrites[StatusResponse] = Json.writes[StatusResponse]

}
