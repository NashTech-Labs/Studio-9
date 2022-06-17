package baile.domain.cv

import baile.domain.cv.CommonTrainParams.InputSize

case class CommonTrainParams(
  inputSize: Option[InputSize],
  loi: Option[Seq[LabelOfInterest]],
  defaultVisualThreshold: Option[Float],
  iouThreshold: Option[Float],
  featureExtractorLearningRate: Option[Double],
  modelLearningRate: Option[Double]
)

object CommonTrainParams {

  case class InputSize(
    width: Int,
    height: Int
  )

}
