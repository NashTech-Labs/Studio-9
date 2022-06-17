package baile.routes.contract

import baile.domain.pipeline.PipelineStatus
import baile.utils.json.EnumWritesBuilder
import play.api.libs.json.Writes

package object pipeline {

  implicit val PipelineStatusWrites: Writes[PipelineStatus] = EnumWritesBuilder.build {
    case PipelineStatus.Idle => "IDLE"
  }

}
