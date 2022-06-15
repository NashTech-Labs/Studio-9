package baile.routes.contract.pipeline

import java.time.Instant
import java.util.UUID

import baile.daocommons.WithId
import baile.domain.pipeline.{ Pipeline, PipelineStatus }
import play.api.libs.json.{ Json, OWrites }

case class PipelineResponse(
  id: String,
  name: String,
  ownerId: UUID,
  status: PipelineStatus,
  created: Instant,
  updated: Instant,
  description: Option[String],
  steps: Seq[PipelineStepInfo],
  inLibrary: Boolean
)

object PipelineResponse {

  def fromDomain(pipeline: WithId[Pipeline]): PipelineResponse = {
    PipelineResponse(
      id = pipeline.id,
      name = pipeline.entity.name,
      status = pipeline.entity.status,
      ownerId = pipeline.entity.ownerId,
      created = pipeline.entity.created,
      updated = pipeline.entity.updated,
      description = pipeline.entity.description,
      steps = pipeline.entity.steps.map(PipelineStepInfo.fromDomain),
      inLibrary = pipeline.entity.inLibrary
    )
  }

  implicit val PipelineResponseFormat: OWrites[PipelineResponse] = Json.writes[PipelineResponse]

}
