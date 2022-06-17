package baile.domain.cv.pipeline

import baile.domain.pipeline.PipelineParams.PipelineParams

sealed trait FeatureExtractorParams

object FeatureExtractorParams {

  case class CreateNewFeatureExtractorParams(
    featureExtractorArchitecture: String,
    pipelineParams: PipelineParams
  ) extends FeatureExtractorParams

  case class UseExistingFeatureExtractorParams(
    featureExtractorModelId: String,
    tuneFeatureExtractor: Boolean
  ) extends FeatureExtractorParams

}
