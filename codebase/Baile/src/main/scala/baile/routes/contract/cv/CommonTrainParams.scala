package baile.routes.contract.cv

import baile.domain.cv.{ CommonTrainParams => DomainCommonTrainParams }
import baile.routes.contract.cv.CommonTrainParams.InputSize
import play.api.libs.json.OFormat
import play.api.libs.json.Json

case class CommonTrainParams(
  inputSize: Option[InputSize],
  loi: Option[Seq[LabelOfInterest]],
  defaultVisualThreshold: Option[Float],
  iouThreshold: Option[Float],
  featureExtractorLearningRate: Option[Double],
  modelLearningRate: Option[Double]
) {

  def toDomain: DomainCommonTrainParams = DomainCommonTrainParams(
    inputSize.map(_.toDomain),
    loi.map(_.map(_.toDomain)),
    defaultVisualThreshold,
    iouThreshold,
    featureExtractorLearningRate,
    modelLearningRate
  )

}

object CommonTrainParams {

  case class InputSize(
    width: Int,
    height: Int
  ) {
    def toDomain: DomainCommonTrainParams.InputSize = DomainCommonTrainParams.InputSize(
      width,
      height
    )
  }

  implicit val InputSizeFormat: OFormat[InputSize] = Json.format[InputSize]

  implicit val CommonTrainParamsFormat: OFormat[CommonTrainParams] = Json.format[CommonTrainParams]

  def fromDomain(params: DomainCommonTrainParams): CommonTrainParams =
    CommonTrainParams(
      inputSize = params.inputSize.map { size =>
        InputSize(size.width, size.height)
      },
      loi = params.loi.map(_.map(LabelOfInterest.fromDomain)),
      defaultVisualThreshold = params.defaultVisualThreshold,
      iouThreshold = params.iouThreshold,
      featureExtractorLearningRate = params.featureExtractorLearningRate,
      modelLearningRate = params.modelLearningRate
    )

}
