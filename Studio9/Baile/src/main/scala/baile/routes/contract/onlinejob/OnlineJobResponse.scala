package baile.routes.contract.onlinejob

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.asset.AssetReference
import baile.domain.onlinejob.{ OnlineJob, OnlineJobOptions, OnlineJobStatus, OnlinePredictionOptions }
import play.api.libs.json.{ Json, Writes }

case class OnlineJobResponse(
  id: String,
  ownerId: String,
  name: String,
  status: OnlineJobStatus,
  created: Instant,
  updated: Instant,
  enabled: Boolean,
  target: AssetReference,
  options: OnlineJobOptionsResponse
)

object OnlineJobResponse {

  import baile.routes.contract.asset.AssetReferenceFormat

  implicit val OnlineJobResponseWrites: Writes[OnlineJobResponse] = Json.writes[OnlineJobResponse]

  def fromDomain(in: WithId[OnlineJob]): OnlineJobResponse = in match {
    case WithId(onlineJob: OnlineJob, id) => OnlineJobResponse(
      id = id,
      ownerId = onlineJob.ownerId.toString,
      name = onlineJob.name,
      status = onlineJob.status,
      created = onlineJob.created,
      updated = onlineJob.updated,
      enabled = onlineJob.enabled,
      target = AssetReference(onlineJob.options.target.id, onlineJob.options.target.`type`),
      options = createOnlineJobOptionsResponse(onlineJob.options)
    )
  }

  private def createOnlineJobOptionsResponse(onlineJobOptions: OnlineJobOptions) = onlineJobOptions match {
    case onlinePredictionOptions: OnlinePredictionOptions =>
      OnlineCVPredictionOptionsResponse.fromDomain(onlinePredictionOptions)
  }

}
