package cortex.api.job.message

import play.api.libs.json.{ Json, OFormat }

case class TaskTimeInfo(taskName: String, timeInfo: TimeInfo)

object TaskTimeInfo {

  implicit val format: OFormat[TaskTimeInfo] = Json.format[TaskTimeInfo]

}
