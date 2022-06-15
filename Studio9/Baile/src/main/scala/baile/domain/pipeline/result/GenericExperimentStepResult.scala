package baile.domain.pipeline.result

import baile.domain.asset.AssetReference

case class GenericExperimentStepResult(
  id: String,
  assets: Seq[AssetReference],
  summaries: Seq[PipelineOperatorApplicationSummary],
  outputValues: Map[Int, PipelineResultValue],
  executionTime: Long,
  failureMessage: Option[String]
)
