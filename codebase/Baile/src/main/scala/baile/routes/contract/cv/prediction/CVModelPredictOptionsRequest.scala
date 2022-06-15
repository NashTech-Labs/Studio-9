package baile.routes.contract.cv.prediction

import baile.domain.cv.prediction.CVModelPredictOptions
import baile.routes.contract.cv.LabelOfInterest
import play.api.libs.json.{ Json, Reads }

case class CVModelPredictOptionsRequest(
  loi: Option[Seq[LabelOfInterest]],
  defaultVisualThreshold: Option[Float],
  iouThreshold: Option[Float]
) {
  def toDomain: CVModelPredictOptions = CVModelPredictOptions(
    loi.map(_.map(_.toDomain)),
    defaultVisualThreshold,
    iouThreshold
  )
}

object CVModelPredictOptionsRequest {
  implicit val CVModelPredictOptionsRequestReads: Reads[CVModelPredictOptionsRequest] =
    Json.reads[CVModelPredictOptionsRequest]
}
