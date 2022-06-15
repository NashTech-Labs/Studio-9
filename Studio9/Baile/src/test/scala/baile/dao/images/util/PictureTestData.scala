package baile.dao.images.util

import baile.domain.images._
import baile.domain.images.augmentation._
import org.mongodb.scala.Document
import org.mongodb.scala.bson.{ BsonDouble, BsonNumber }

object PictureTestData {

  val PictureEntity = Picture(
    albumId = "albumId",
    filePath = "filePath",
    fileName = "fileName",
    fileSize = Some(0l),
    caption = Some("caption"),
    predictedCaption = Some("predictedCaption"),
    tags = Seq(PictureTag("l", Some(PictureTagArea(0, 0, 0, 0)))),
    predictedTags = Seq(PictureTag("l", None, Some(1.3))),
    meta = Map.empty,
    originalPictureId = None,
    appliedAugmentations = None
  )
  val PictureDoc = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> None,
    "appliedAugmentations" -> None
  )
  val PictureEntityWithNone = Picture(
    albumId = "albumId",
    filePath = "filePath",
    fileName = "fileName",
    fileSize = None,
    caption = None,
    predictedCaption = None,
    tags = Seq(PictureTag("l", Some(PictureTagArea(0, 0, 0, 0)))),
    predictedTags = Seq(PictureTag("l", None, Some(1.3))),
    meta = Map.empty,
    originalPictureId = None,
    appliedAugmentations = None
  )
  val PictureDocWithNone = Document(
    "albumId" -> PictureEntityWithNone.albumId,
    "filePath" -> PictureEntityWithNone.filePath,
    "fileName" -> PictureEntityWithNone.fileName,
    "fileSize" -> PictureEntityWithNone.fileSize,
    "caption" -> PictureEntityWithNone.caption,
    "predictedCaption" -> PictureEntityWithNone.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> None,
    "appliedAugmentations" -> None
  )
  val PictureEntityWithMeta = Picture(
    albumId = "albumId",
    filePath = "filePath",
    fileName = "fileName",
    fileSize = None,
    caption = None,
    predictedCaption = None,
    tags = Seq(PictureTag("l", Some(PictureTagArea(0, 0, 0, 0)))),
    predictedTags = Seq(PictureTag("l", None, Some(1.3))),
    meta = Map("foo" -> "bar"),
    originalPictureId = None,
    appliedAugmentations = None
  )
  val PictureDocWithMeta = Document(
    "albumId" -> PictureEntityWithNone.albumId,
    "filePath" -> PictureEntityWithNone.filePath,
    "fileName" -> PictureEntityWithNone.fileName,
    "fileSize" -> PictureEntityWithNone.fileSize,
    "caption" -> PictureEntityWithNone.caption,
    "predictedCaption" -> PictureEntityWithNone.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document(
      "foo" -> "bar"
    ),
    "originalPictureId" -> None,
    "appliedAugmentations" -> None
  )
  val AppliedRotationEntity = AppliedRotationParams(10.0f, true)
  val AppliedCroppingEntity = AppliedCroppingParams(10.0f, true)
  val AppliedOcclusionEntity = AppliedOcclusionParams(10.0f, OcclusionMode.Background, true, 1)
  val AppliedShearingEntity = AppliedShearingParams(10.0f, true)
  val AppliedMirroringEntity = AppliedMirroringParams(MirroringAxisToFlip.Horizontal)
  val AppliedBlurringEntity = AppliedBlurringParams(10.0f)
  val AppliedSaltPepperEntity = AppliedSaltPepperParams(10.0f, 10.0f)
  val AppliedPhotometricDistortEntity = AppliedPhotometricDistortParams(10.0f, 2.0f, 5.0f, 3.0f)
  val AppliedZoomInEntity = AppliedZoomInParams(10.0f, true)
  val AppliedZoomOutEntity = AppliedZoomOutParams(10.0f, true)
  val AppliedNoisingEntity = AppliedNoisingParams(10.0f)
  val AppliedTranslationEntity = AppliedTranslationParams(10.0f, TranslationMode.Constant, true)

  val ExtraParams = Map("param1" -> 1F, "param2" -> 2F)
  val InternalParams = Map("ip1" -> 3F, "ip2" -> 4F)

  val OccludedPictureEntity = PictureEntity.copy(
    appliedAugmentations = Some(Seq(buildAppliedAugmentation(AppliedOcclusionEntity))),
    originalPictureId = Some("1")
  )
  val OccludedPictureDoc: Document = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> "1",
    "appliedAugmentations" -> Seq(Document(
      "generalParams" -> Document(
        "occAreaFraction" -> 10.0,
        "mode" -> AppliedOcclusionEntity.mode.toString,
        "isSarAlbum" -> AppliedOcclusionEntity.isSarAlbum,
        "tarWinSize" -> AppliedOcclusionEntity.tarWinSize,
        "augmentationType" -> "occlusion"
      ),
      "extraParams" -> Document(
        "param1" -> BsonDouble(1F),
        "param2" -> BsonDouble(2F)
      ),
      "internalParams" -> Document(
        "ip1" -> BsonDouble(3F),
        "ip2" -> BsonDouble(4F)
      )
    ))
  )
  val RotatedPictureEntity = PictureEntity.copy(
    appliedAugmentations = Some(Seq(buildAppliedAugmentation(AppliedRotationEntity))),
    originalPictureId = Some("1")
  )
  val RotatedPictureDoc = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> "1",
    "appliedAugmentations" -> Seq(Document(
      "generalParams" -> Document(
        "angle" -> 10.0,
        "resize" -> AppliedRotationEntity.resize,
        "augmentationType" -> "rotation"
      ),
      "extraParams" -> Document(
        "param1" -> BsonDouble(1F),
        "param2" -> BsonDouble(2F)
      ),
      "internalParams" -> Document(
        "ip1" -> BsonDouble(3F),
        "ip2" -> BsonDouble(4F)
      )
    ))
  )
  val ShearedPictureEntity = PictureEntity.copy(
    appliedAugmentations = Some(Seq(buildAppliedAugmentation(AppliedShearingEntity))),
    originalPictureId = Some("1")
  )
  val ShearedPictureDoc = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> "1",
    "appliedAugmentations" -> Seq(Document(
      "generalParams" -> Document(
        "angle" -> 10.0,
        "resize" -> AppliedShearingEntity.resize,
        "augmentationType" -> "shearing"
      ),
      "extraParams" -> Document(
        "param1" -> BsonDouble(1F),
        "param2" -> BsonDouble(2F)
      ),
      "internalParams" -> Document(
        "ip1" -> BsonDouble(3F),
        "ip2" -> BsonDouble(4F)
      )
    ))
  )
  val CroppedPictureEntity = PictureEntity.copy(
    appliedAugmentations = Some(Seq(buildAppliedAugmentation(AppliedCroppingEntity))),
    originalPictureId = Some("1")
  )
  val CroppedPictureDoc = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> "1",
    "appliedAugmentations" -> Seq(Document(
      "generalParams" -> Document(
        "cropAreaFraction" -> 10.0,
        "resize" -> AppliedCroppingEntity.resize,
        "augmentationType" -> "cropping"
      ),
      "extraParams" -> Document(
        "param1" -> BsonDouble(1F),
        "param2" -> BsonDouble(2F)
      ),
      "internalParams" -> Document(
        "ip1" -> BsonDouble(3F),
        "ip2" -> BsonDouble(4F)
      )
    ))
  )

  val MirroredPictureEntity = PictureEntity.copy(
    appliedAugmentations = Some(Seq(buildAppliedAugmentation(AppliedMirroringEntity))),
    originalPictureId = Some("1")
  )
  val MirroredPictureDoc: Document = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> "1",
    "appliedAugmentations" -> Seq(Document(
      "generalParams" -> Document(
        "axisFlipped" -> "Horizontal",
        "augmentationType" -> "mirroring"
      ),
      "extraParams" -> Document(
        "param1" -> BsonDouble(1F),
        "param2" -> BsonDouble(2F)
      ),
      "internalParams" -> Document(
        "ip1" -> BsonDouble(3F),
        "ip2" -> BsonDouble(4F)
      )
    ))
  )
  val BlurredPictureEntity = PictureEntity.copy(
    appliedAugmentations = Some(Seq(buildAppliedAugmentation(AppliedBlurringEntity))),
    originalPictureId = Some("1")
  )
  val BlurredPictureDoc: Document = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> "1",
    "appliedAugmentations" -> Seq(Document(
      "generalParams" -> Document(
        "sigma" -> BsonNumber(AppliedBlurringEntity.sigma),
        "augmentationType" -> "blurring"
      ),
      "extraParams" -> Document(
        "param1" -> BsonDouble(1F),
        "param2" -> BsonDouble(2F)
      ),
      "internalParams" -> Document(
        "ip1" -> BsonDouble(3F),
        "ip2" -> BsonDouble(4F)
      )
    ))
  )
  val SaltPepperPictureEntity = PictureEntity.copy(
    appliedAugmentations = Some(Seq(buildAppliedAugmentation(AppliedSaltPepperEntity))),
    originalPictureId = Some("1")
  )
  val SaltPepperPictureDoc: Document = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> "1",
    "appliedAugmentations" -> Seq(Document(
      "generalParams" -> Document(
        "knockoutFraction" -> 10.0,
        "pepperProbability" -> 10.0,
        "augmentationType" -> "saltPepper"
      ),
      "extraParams" -> Document(
        "param1" -> BsonDouble(1F),
        "param2" -> BsonDouble(2F)
      ),
      "internalParams" -> Document(
        "ip1" -> BsonDouble(3F),
        "ip2" -> BsonDouble(4F)
      )
    ))
  )
  val PhotometricDistortPictureEntity = PictureEntity.copy(
    appliedAugmentations = Some(Seq(buildAppliedAugmentation(AppliedPhotometricDistortEntity))),
    originalPictureId = Some("1")
  )
  val PhotometricDistortPictureDoc: Document = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> "1",
    "appliedAugmentations" -> Seq(Document(
      "generalParams" -> Document(
        "alphaContrast" -> 10.0,
        "deltaMax" -> 2.0,
        "alphaSaturation" -> 5.0,
        "deltaHue" -> 3.0,
        "augmentationType" -> "photometricDistort"
      ),
      "extraParams" -> Document(
        "param1" -> BsonDouble(1F),
        "param2" -> BsonDouble(2F)
      ),
      "internalParams" -> Document(
        "ip1" -> BsonDouble(3F),
        "ip2" -> BsonDouble(4F)
      )
    ))
  )
  val ZoomedInPictureEntity = PictureEntity.copy(
    appliedAugmentations = Some(Seq(buildAppliedAugmentation(AppliedZoomInEntity))),
    originalPictureId = Some("1")
  )
  val ZoomedInPictureDoc: Document = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> "1",
    "appliedAugmentations" -> Seq(Document(
      "generalParams" -> Document(
        "magnification" -> 10.0,
        "resize" -> AppliedZoomInEntity.resize,
        "augmentationType" -> "zoomIn"
      ),
      "extraParams" -> Document(
        "param1" -> BsonDouble(1F),
        "param2" -> BsonDouble(2F)
      ),
      "internalParams" -> Document(
        "ip1" -> BsonDouble(3F),
        "ip2" -> BsonDouble(4F)
      )
    ))
  )
  val ZoomedOutPictureEntity = PictureEntity.copy(
    appliedAugmentations = Some(Seq(buildAppliedAugmentation(AppliedZoomOutEntity))),
    originalPictureId = Some("1")
  )
  val ZoomedOutPictureDoc: Document = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> "1",
    "appliedAugmentations" -> Seq(Document(
      "generalParams" -> Document(
        "shrinkFactor" -> 10.0,
        "resize" -> AppliedZoomOutEntity.resize,
        "augmentationType" -> "zoomOut"
      ),
      "extraParams" -> Document(
        "param1" -> BsonDouble(1F),
        "param2" -> BsonDouble(2F)
      ),
      "internalParams" -> Document(
        "ip1" -> BsonDouble(3F),
        "ip2" -> BsonDouble(4F)
      )
    ))
  )
  val NoisingPictureEntity = PictureEntity.copy(
    appliedAugmentations = Some(Seq(buildAppliedAugmentation(AppliedNoisingEntity))),
    originalPictureId = Some("1")
  )
  val NoisingPictureDoc: Document = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> "1",
    "appliedAugmentations" -> Seq(Document(
      "generalParams" -> Document(
        "noiseSignalRatio" -> 10.0,
        "augmentationType" -> "noising"
      ),
      "extraParams" -> Document(
        "param1" -> BsonDouble(1F),
        "param2" -> BsonDouble(2F)
      ),
      "internalParams" -> Document(
        "ip1" -> BsonDouble(3F),
        "ip2" -> BsonDouble(4F)
      )
    ))
  )
  val TranslationPictureEntity = PictureEntity.copy(
    appliedAugmentations = Some(Seq(buildAppliedAugmentation(AppliedTranslationEntity))),
    originalPictureId = Some("1")
  )
  val TranslationPictureDoc: Document = Document(
    "albumId" -> PictureEntity.albumId,
    "filePath" -> PictureEntity.filePath,
    "fileName" -> PictureEntity.fileName,
    "fileSize" -> PictureEntity.fileSize,
    "caption" -> PictureEntity.caption,
    "predictedCaption" -> PictureEntity.predictedCaption,
    "tags" -> Seq(
      Document(
        "label" -> "l",
        "area" -> Document("top" -> 0, "left" -> 0, "height" -> 0, "width" -> 0),
        "confidence" -> None
      )
    ),
    "predictedTags" -> Seq(Document("label" -> "l", "area" -> None, "confidence" -> Some(1.3))),
    "meta" -> Document.empty,
    "originalPictureId" -> "1",
    "appliedAugmentations" -> Seq(Document(
      "generalParams" -> Document(
        "translateFraction" -> 10.0,
        "mode" -> AppliedTranslationEntity.mode.toString,
        "resize" -> AppliedTranslationEntity.resize,
        "augmentationType" -> "translation"
      ),
      "extraParams" -> Document(
        "param1" -> BsonDouble(1F),
        "param2" -> BsonDouble(2F)
      ),
      "internalParams" -> Document(
        "ip1" -> BsonDouble(3F),
        "ip2" -> BsonDouble(4F)
      )
    ))
  )

  private def buildAppliedAugmentation(generalParams: AppliedAugmentationParams) =
    AppliedAugmentation(generalParams, ExtraParams, InternalParams)

}
