package baile.domain.cv.prediction

import baile.domain.cv.LabelOfInterest

case class CVModelPredictOptions(
  loi: Option[Seq[LabelOfInterest]],
  defaultVisualThreshold: Option[Float],
  iouThreshold: Option[Float]
)
