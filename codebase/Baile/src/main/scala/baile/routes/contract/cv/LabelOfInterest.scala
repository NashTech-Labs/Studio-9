package baile.routes.contract.cv

import baile.domain.cv.{ LabelOfInterest => DomainLabelOfInterest }
import play.api.libs.json.{ Json, OFormat }

case class LabelOfInterest(
  label: String,
  threshold: Double
) {

  def toDomain: DomainLabelOfInterest = DomainLabelOfInterest(
    label,
    threshold
  )

}

object LabelOfInterest {

  implicit val LabelOfInterestFormat: OFormat[LabelOfInterest] =
    Json.format[LabelOfInterest]

  def fromDomain(loi: DomainLabelOfInterest): LabelOfInterest =
    LabelOfInterest(loi.label, loi.threshold)

}
