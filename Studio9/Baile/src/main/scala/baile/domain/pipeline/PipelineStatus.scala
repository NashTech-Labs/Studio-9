package baile.domain.pipeline

import baile.domain.asset.AssetStatus

sealed trait PipelineStatus extends AssetStatus

object PipelineStatus {

  case object Idle extends PipelineStatus

}
