package baile.routes.contract.cv

import baile.domain.cv.pipeline.{ FeatureExtractorParams, CVTLTrainStep1Params => DomainStep1Params }
import baile.routes.contract.cv.CVTLTrainStep1Params._
import baile.routes.contract.cv.model.CVModelType
import baile.routes.contract.pipeline.PipelineParams
import play.api.libs.functional.syntax._
import play.api.libs.json._

case class CVTLTrainStep1Params(
  feParams: FEParams,
  modelType: CVModelType.TLConsumer,
  input: String,
  testInput: Option[String],
  augmentationOptions: Option[CVAugmentationOptions],
  params: PipelineParams,
  trainParams: Option[CommonTrainParams]
) {

  def toDomain: DomainStep1Params = DomainStep1Params(
    feParams = feParams match {
      case NewFEParams(feArchitecture, fePipelineParams) =>
        FeatureExtractorParams.CreateNewFeatureExtractorParams(
          feArchitecture,
          fePipelineParams.toDomain
        )
      case ExistingFEParams(feModelId, tuneFeatureExtractor) =>
        FeatureExtractorParams.UseExistingFeatureExtractorParams(
          feModelId,
          tuneFeatureExtractor.getOrElse(false)
        )
    },
    modelType = modelType.toDomain,
    modelParams = params.toDomain,
    inputAlbumId = input,
    testInputAlbumId = testInput,
    automatedAugmentationParams = augmentationOptions.map(_.toDomain),
    trainParams = trainParams.map(_.toDomain)
  )

}

object CVTLTrainStep1Params {

  sealed trait FEParams

  case class NewFEParams(
    architecture: String,
    featureExtractorParams: PipelineParams
  ) extends FEParams

  case class ExistingFEParams(
    featureExtractorModelId: String,
    tuneFeatureExtractor: Option[Boolean]
  ) extends FEParams


  private def fromFEParams(feParams: FeatureExtractorParams): FEParams = {
    feParams match {
      case FeatureExtractorParams.CreateNewFeatureExtractorParams(featureExtractorArchitecture, pipelineParams) =>
        NewFEParams(
          featureExtractorArchitecture,
          PipelineParams.fromDomain(pipelineParams)
        )
      case FeatureExtractorParams.UseExistingFeatureExtractorParams(featureExtractorModelId, tuneFeatureExtractor) =>
        ExistingFEParams(
          featureExtractorModelId,
          Some(tuneFeatureExtractor)
        )
    }
  }

  def fromDomain(in: DomainStep1Params): CVTLTrainStep1Params = {
    val feParams = fromFEParams(in.feParams)
    CVTLTrainStep1Params(
      feParams = feParams,
      modelType = CVModelType.TLConsumer.fromDomain(in.modelType),
      params = PipelineParams.fromDomain(in.modelParams),
      input = in.inputAlbumId,
      testInput = in.testInputAlbumId,
      augmentationOptions = in.automatedAugmentationParams.map(CVAugmentationOptions.fromDomain),
      trainParams = in.trainParams.map(CommonTrainParams.fromDomain)
    )
  }

  implicit val NewFEParamsFormat: OFormat[NewFEParams] = Json.format[NewFEParams]
  implicit val ExistingFEParamsFormat: OFormat[ExistingFEParams] = Json.format[ExistingFEParams]

  implicit val FEPramsReads: Reads[FEParams] =
    NewFEParamsFormat.map(_.asInstanceOf[FEParams]) or ExistingFEParamsFormat.map(_.asInstanceOf[FEParams])

  implicit val FEPramsWrites: OWrites[FEParams] = new OWrites[FEParams] {
    override def writes(o: FEParams): JsObject = o match {
      case newFEParams: NewFEParams => NewFEParamsFormat.writes(newFEParams)
      case existingFEParams: ExistingFEParams => ExistingFEParamsFormat.writes(existingFEParams)
    }
  }

  implicit val CVTLTrainStep1ParamsFormat: OFormat[CVTLTrainStep1Params] = (
    __.format[FEParams] ~
    (__ \ "modelType").format[CVModelType.TLConsumer] ~
    (__ \ "input").format[String] ~
    (__ \ "testInput").formatNullable[String] ~
    (__ \ "augmentationOptions").formatNullable[CVAugmentationOptions] ~
    (__ \ "params").format[PipelineParams] ~
    (__ \ "trainParams").formatNullable[CommonTrainParams]
  )(CVTLTrainStep1Params.apply, unlift(CVTLTrainStep1Params.unapply))

}
