package baile.routes.contract.tabular.model.summary

import baile.domain.tabular.model.summary.ClassConfusion
import play.api.libs.json.{ Json, OWrites }

case class ConfusionMatrixRowResponse(
  className: String,
  truePositive: Int,
  trueNegative: Int,
  falsePositive: Int,
  falseNegative: Int
)

object ConfusionMatrixRowResponse {

  implicit val ConfusionMatrixRowResponseWrites: OWrites[ConfusionMatrixRowResponse] =
    Json.writes[ConfusionMatrixRowResponse]

  def fromDomain(classConfusion: ClassConfusion): ConfusionMatrixRowResponse =
    ConfusionMatrixRowResponse(
      className = classConfusion.className,
      truePositive = classConfusion.truePositive,
      trueNegative = classConfusion.trueNegative,
      falsePositive = classConfusion.falsePositive,
      falseNegative = classConfusion.falseNegative
    )

}
