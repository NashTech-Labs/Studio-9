package baile.routes.contract.process

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.process.{ Process, ProcessStatus }
import baile.routes.contract.asset._
import play.api.libs.json.{ Json, Writes }

case class ProcessResponse(
  id: String,
  target: AssetType,
  targetId: String,
  status: ProcessStatus,
  progress: Double,
  estimate: Option[Int],
  created: Instant,
  started: Option[Instant],
  completed: Option[Instant],
  cause: Option[String],
  jobType: JobType
)

object ProcessResponse {

  def fromDomain(
    process: WithId[Process],
    appendErrorDetails: Boolean = false,
    jobType: JobType
  ): ProcessResponse = {
    val entity = process.entity
    ProcessResponse(
      id = process.id,
      target = entity.targetType,
      targetId = entity.targetId,
      status = entity.status,
      progress = entity.progress.getOrElse(0),
      estimate = entity.estimatedTimeRemaining.map(_.toSeconds.toInt),
      created = entity.created,
      started = entity.started,
      completed = entity.completed,
      cause =
        if (appendErrorDetails) {
          entity.errorCauseMessage.map(_ + ":\n" + entity.errorDetails.getOrElse("No details"))
        } else {
          entity.errorCauseMessage
        },
      jobType = jobType
    )
  }

  implicit val ProcessResponseWrites: Writes[ProcessResponse] = Json.writes[ProcessResponse]

}
