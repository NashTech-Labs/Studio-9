package baile.routes.contract.images.augmentation

import baile.domain.images.augmentation.AppliedAugmentation
import baile.utils.json.CommonFormats.FloatWrites
import play.api.libs.json.{ Json, OWrites }

case class PictureAugmentationAppliedResponse(
  generalParams: PictureAugmentationParamsResponse,
  extraParams: Map[String, Float]
)

object PictureAugmentationAppliedResponse {

  def fromDomain(appliedAugmentation: AppliedAugmentation): PictureAugmentationAppliedResponse =
    PictureAugmentationAppliedResponse(
      PictureAugmentationParamsResponse.fromDomain(appliedAugmentation.generalParams),
      appliedAugmentation.extraParams
    )

  implicit val AppliedAugmentationResponseWrites: OWrites[PictureAugmentationAppliedResponse] =
    OWrites[PictureAugmentationAppliedResponse] { response =>
      val generalParamsJson = Json.toJsObject(response.generalParams)
      generalParamsJson + ("extraParams" -> Json.toJson(response.extraParams))
    }

}
