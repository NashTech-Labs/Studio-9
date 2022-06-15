package baile.routes.contract.cv

import baile.domain.cv.AugmentationSummaryCell
import baile.routes.contract.images.augmentation.AlbumAugmentationStep
import play.api.libs.json.{ Json, OWrites }

case class AlbumAugmentationSummaryResponse(
  augmentation: AlbumAugmentationStep,
  count: Long
)

object AlbumAugmentationSummaryResponse {

  def fromDomain(
    summaryCell: AugmentationSummaryCell
  ): AlbumAugmentationSummaryResponse = AlbumAugmentationSummaryResponse(
    augmentation = AlbumAugmentationStep.fromDomain(summaryCell.augmentationParams),
    count = summaryCell.imagesCount
  )

  implicit val AlbumAugmentationSummaryResponseWrites: OWrites[AlbumAugmentationSummaryResponse] =
    Json.writes[AlbumAugmentationSummaryResponse]

}
