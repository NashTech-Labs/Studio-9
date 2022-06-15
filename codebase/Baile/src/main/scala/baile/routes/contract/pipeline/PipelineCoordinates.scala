package baile.routes.contract.pipeline

import baile.domain.pipeline.{ PipelineCoordinates => DomainPipelineCoordinates }
import play.api.libs.json.{ Json, OFormat }

case class PipelineCoordinates(
  x: Int,
  y: Int
) {

  def toDomain: DomainPipelineCoordinates =
    DomainPipelineCoordinates(
      x,
      y
    )

}

object PipelineCoordinates {

  implicit val PipelineCoordinatesFormat: OFormat[PipelineCoordinates] = Json.format[PipelineCoordinates]

  def fromDomain(coordinates: DomainPipelineCoordinates): PipelineCoordinates = {
    PipelineCoordinates(
      coordinates.x,
      coordinates.y
    )
  }

}
