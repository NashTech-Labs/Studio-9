package cortex.api.job.message

import java.util.UUID

import play.api.libs.json.{ Json, OFormat }

case class JobMessageMeta(jobId: UUID, jobType: Option[String] = None)

object JobMessageMeta {

  implicit val format: OFormat[JobMessageMeta] = Json.format[JobMessageMeta]

}

