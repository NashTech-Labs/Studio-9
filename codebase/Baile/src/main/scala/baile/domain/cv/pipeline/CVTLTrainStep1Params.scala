package baile.domain.cv.pipeline

import baile.domain.pipeline.PipelineParams.PipelineParams
import baile.domain.cv.CommonTrainParams
import baile.domain.cv.model.{ AutomatedAugmentationParams, CVModelType }

case class CVTLTrainStep1Params(
  feParams: FeatureExtractorParams,
  modelType: CVModelType.TLConsumer,
  modelParams: PipelineParams,
  inputAlbumId: String,
  testInputAlbumId: Option[String],
  automatedAugmentationParams: Option[AutomatedAugmentationParams],
  trainParams: Option[CommonTrainParams]
)
