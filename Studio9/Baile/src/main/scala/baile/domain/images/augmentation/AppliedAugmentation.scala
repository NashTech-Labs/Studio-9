package baile.domain.images.augmentation

case class AppliedAugmentation(
  generalParams: AppliedAugmentationParams,
  extraParams: Map[String, Float],
  internalParams: Map[String, Float]
)
