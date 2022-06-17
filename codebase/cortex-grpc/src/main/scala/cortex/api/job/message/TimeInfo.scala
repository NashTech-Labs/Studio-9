package cortex.api.job.message

import java.util.Date

import play.api.libs.json.{ Json, OFormat }

case class TimeInfo(submittedAt: Date, startedAt: Option[Date], completedAt: Option[Date])

object TimeInfo {

  implicit val format: OFormat[TimeInfo] = Json.format[TimeInfo]

}
