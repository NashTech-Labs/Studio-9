package baile.routes.contract.cv

import baile.domain.cv.pipeline.{ CVTLTrainStep2Params => DomainStep2Params }
import baile.routes.contract.cv.model.CVModelType
import baile.routes.contract.pipeline.PipelineParams
import play.api.libs.json.{ Json, OFormat }

case class CVTLTrainStep2Params(
  tuneFeatureExtractor: Option[Boolean],
  modelType: CVModelType.TLConsumer,
  params: PipelineParams,
  input: String,
  testInput: Option[String],
  augmentationOptions: Option[CVAugmentationOptions],
  trainParams: Option[CommonTrainParams]
) {

  def toDomain: DomainStep2Params = DomainStep2Params(
    tuneFeatureExtractor = tuneFeatureExtractor.getOrElse(false),
    modelType = modelType.toDomain,
    modelParams = params.toDomain,
    inputAlbumId = input,
    testInputAlbumId = testInput,
    automatedAugmentationParams = augmentationOptions.map(_.toDomain),
    trainParams = trainParams.map(_.toDomain)
  )

}

object CVTLTrainStep2Params {


  def fromDomain(in: DomainStep2Params): CVTLTrainStep2Params = CVTLTrainStep2Params(
    tuneFeatureExtractor = Some(in.tuneFeatureExtractor),
    modelType = CVModelType.TLConsumer.fromDomain(in.modelType),
    params = PipelineParams.fromDomain(in.modelParams),
    input = in.inputAlbumId,
    testInput = in.testInputAlbumId,
    augmentationOptions = in.automatedAugmentationParams.map(CVAugmentationOptions.fromDomain),
    trainParams = in.trainParams.map(CommonTrainParams.fromDomain)
  )

  implicit val CVTLTrainStep2ParamsFormat: OFormat[CVTLTrainStep2Params] =
    Json.format[CVTLTrainStep2Params]

}
