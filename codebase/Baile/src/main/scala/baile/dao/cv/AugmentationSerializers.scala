package baile.dao.cv

import baile.dao.mongo.BsonHelpers._
import baile.domain.cv.AugmentationSummaryCell
import baile.domain.cv.model.AutomatedAugmentationParams
import baile.domain.images.augmentation.MirroringAxisToFlip.{ Both, Horizontal, Vertical }
import baile.domain.images.augmentation.OcclusionMode.{ Background, Zero }
import baile.domain.images.augmentation.TranslationMode.{ Constant, Reflect }
import baile.domain.images.augmentation._
import org.mongodb.scala.Document
import org.mongodb.scala.bson.{ BsonArray, BsonBoolean, BsonInt32, BsonInt64, BsonNumber, BsonString }

object AugmentationSerializers {

  def augmentationSummaryCellToDocument(augmentationSummaryCell: AugmentationSummaryCell): Document = Document(
    "augmentationParams" -> AugmentationSerializers.augmentationParamsToDocument(
      augmentationSummaryCell.augmentationParams
    ),
    "imagesCount" -> BsonInt64(augmentationSummaryCell.imagesCount)
  )

  def documentToAugmentationSummaryCell(document: Document): AugmentationSummaryCell = AugmentationSummaryCell(
    augmentationParams = AugmentationSerializers.documentToAugmentationParams(
      document.getChildMandatory("augmentationParams")
    ),
    imagesCount = document.getMandatory[BsonInt64]("imagesCount").getValue
  )

  def documentToAugmentationParams(document: Document): AugmentationParams = {
    stringToAugmentationType(document.getMandatory[BsonString]("augmentationType").getValue) match {
      case AugmentationType.Rotation => documentToRotationParams(document)
      case AugmentationType.Translation => documentToTranslationParams(document)
      case AugmentationType.Noising => documentToNoisingParams(document)
      case AugmentationType.Shearing => documentToShearingParams(document)
      case AugmentationType.ZoomIn => documentToZoomInParams(document)
      case AugmentationType.ZoomOut => documentToZoomOutParams(document)
      case AugmentationType.Occlusion => documentToOcclusionParams(document)
      case AugmentationType.SaltPepper => documentToSaltPepperParams(document)
      case AugmentationType.Mirroring => documentToMirroringParams(document)
      case AugmentationType.Cropping => documentToCroppingParams(document)
      case AugmentationType.Blurring => documentToBlurringParams(document)
      case AugmentationType.PhotoDistort => documentToPhotometricDistortParams(document)
    }
  }

  def documentToRotationParams(document: Document): RotationParams =
    RotationParams(
      angles = document.getMandatory[BsonArray]("angles").map(_.asDouble().getValue.toFloat),
      resize = document.getMandatory[BsonBoolean]("resize").getValue,
      bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").intValue()
    )

  def documentToTranslationParams(document: Document): TranslationParams =
    TranslationParams(
      translateFractions = document.getMandatory[BsonArray]("translateFractions").map(_.asDouble().getValue.toFloat),
      mode = document.getMandatory[BsonString]("mode").getValue match {
        case "Reflect" => TranslationMode.Reflect
        case "Constant" => TranslationMode.Constant
      },
      resize = document.getMandatory[BsonBoolean]("resize").getValue,
      bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").intValue()
    )

  def documentToShearingParams(document: Document): ShearingParams =
    ShearingParams(
      angles = document.getMandatory[BsonArray]("angles").map(_.asDouble().getValue.toFloat),
      resize = document.getMandatory[BsonBoolean]("resize").getValue,
      bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").intValue()
    )

  def documentToNoisingParams(document: Document): NoisingParams = NoisingParams(
    noiseSignalRatios = document.getMandatory[BsonArray]("noiseSignalRatios").map(_.asDouble().getValue.toFloat),
    bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").getValue
  )

  def documentToZoomInParams(document: Document): ZoomInParams = ZoomInParams(
    magnifications = document.getMandatory[BsonArray]("magnifications").map(_.asDouble().getValue.toFloat),
    resize = document.getMandatory[BsonBoolean]("resize").getValue,
    bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").intValue()
  )

  def documentToZoomOutParams(document: Document): ZoomOutParams = ZoomOutParams(
    shrinkFactors = document.getMandatory[BsonArray]("shrinkFactors").map(_.asDouble().getValue.toFloat),
    resize = document.getMandatory[BsonBoolean]("resize").getValue,
    bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").intValue()
  )

  def documentToOcclusionParams(document: Document): OcclusionParams =
    OcclusionParams(
      occAreaFractions = document.getMandatory[BsonArray]("occAreaFractions")
        .map(_.asDouble().getValue.toFloat),
      mode = document.getMandatory[BsonString]("mode").getValue match {
        case "Background" => OcclusionMode.Background
        case "Zero" => OcclusionMode.Zero
      },
      isSarAlbum = document.getMandatory[BsonBoolean]("isSarAlbum").getValue,
      tarWinSize = document.getMandatory[BsonInt32]("tarWinSize").getValue,
      bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").getValue
    )

  def documentToSaltPepperParams(document: Document): SaltPepperParams =
    SaltPepperParams(
      knockoutFractions = document.getMandatory[BsonArray]("knockoutFractions").map(_.asDouble().getValue.toFloat),
      pepperProbability = document.getMandatory[BsonNumber]("pepperProbability").doubleValue().toFloat,
      bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").intValue()
    )

  def documentToMirroringParams(document: Document): MirroringParams =
    MirroringParams(
      axesToFlip = document.getMandatory[BsonArray]("axesFlipped").map(_.asString().getValue) map {
        case "Horizontal" => MirroringAxisToFlip.Horizontal
        case "Vertical" => MirroringAxisToFlip.Vertical
        case "Both" => MirroringAxisToFlip.Both
      },
      bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").intValue()
    )

  def documentToCroppingParams(document: Document): CroppingParams =
    CroppingParams(
      cropAreaFractions = document.getMandatory[BsonArray]("cropAreaFractions").map(_.asDouble().getValue.toFloat),
      cropsPerImage = document.getMandatory[BsonInt32]("cropsPerImage").intValue(),
      resize = document.getMandatory[BsonBoolean]("resize").getValue,
      bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").intValue()
    )

  def documentToBlurringParams(document: Document): BlurringParams =
    BlurringParams(
      sigmaList = document.getMandatory[BsonArray]("sigmas").map(_.asDouble().getValue.toFloat),
      bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").intValue()
    )

  def documentToPhotometricDistortParams(document: Document): PhotometricDistortParams =
    PhotometricDistortParams(
      PhotometricDistortAlphaBounds(
        min = document.getMandatory[BsonNumber]("alphaMin").doubleValue().toFloat,
        max = document.getMandatory[BsonNumber]("alphaMax").doubleValue().toFloat
      ),
      deltaMax = document.getMandatory[BsonNumber]("deltaMax").doubleValue().toFloat,
      bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").intValue()
    )

  def documentToAutomatedAugmentationParams(document: Document): AutomatedAugmentationParams = {
    AutomatedAugmentationParams(
      augmentations = document.getMandatory[BsonArray]("augmentations").map(_.asDocument).map(
        params => documentToAugmentationParams(params)
      ),
      bloatFactor = document.getMandatory[BsonInt32]("bloatFactor").getValue,
      generateSampleAlbum = document.getMandatory[BsonBoolean]("generateSampleAlbum").getValue
    )
  }

  def automatedAugmentationParamsToDocument(automatedAugmentationParams: AutomatedAugmentationParams): Document =
    Document(
      "augmentations" -> automatedAugmentationParams.augmentations.map(augmentationParamsToDocument),
      "bloatFactor" -> BsonNumber(automatedAugmentationParams.bloatFactor),
      "generateSampleAlbum" -> BsonBoolean(automatedAugmentationParams.generateSampleAlbum)
    )

  def augmentationParamsToDocument(augmentationParams: AugmentationParams): Document =
    augmentationParams match {
      case RotationParams(angles, resize, bloatFactor) =>
        Document(
          "angles" -> angles.map(BsonNumber(_)),
          "resize" -> BsonBoolean(resize),
          "bloatFactor" -> BsonInt32(bloatFactor),
          "augmentationType" -> BsonString(augmentationTypeToString(AugmentationType.Rotation))
        )
      case ShearingParams(angles, resize, bloatFactor) => Document(
        "angles" -> angles.map(BsonNumber(_)),
        "resize" -> BsonBoolean(resize),
        "bloatFactor" -> BsonInt32(bloatFactor),
        "augmentationType" -> BsonString(augmentationTypeToString(AugmentationType.Shearing))
      )
      case NoisingParams(noiseSignalRatios, bloatFactor) => Document(
        "noiseSignalRatios" -> noiseSignalRatios.map(BsonNumber(_)),
        "bloatFactor" -> BsonInt32(bloatFactor),
        "augmentationType" -> BsonString(augmentationTypeToString(AugmentationType.Noising))
      )
      case ZoomInParams(ratios, resize, bloatFactor) => Document(
        "magnifications" -> ratios.map(BsonNumber(_)),
        "resize" -> BsonBoolean(resize),
        "bloatFactor" -> BsonInt32(bloatFactor),
        "augmentationType" -> BsonString(augmentationTypeToString(AugmentationType.ZoomIn))
      )
      case ZoomOutParams(ratios, resize, bloatFactor) => Document(
        "shrinkFactors" -> ratios.map(BsonNumber(_)),
        "resize" -> BsonBoolean(resize),
        "bloatFactor" -> BsonInt32(bloatFactor),
        "augmentationType" -> BsonString(augmentationTypeToString(AugmentationType.ZoomOut))
      )
      case OcclusionParams(occAreaFractions, mode, isSarAlbum, tarWinSize, bloatFactor) => Document(
        "occAreaFractions" -> occAreaFractions.map(BsonNumber(_)),
        "mode" -> BsonString(mode match {
          case Background => "Background"
          case Zero => "Zero"
        }),
        "isSarAlbum" -> BsonBoolean(isSarAlbum),
        "tarWinSize" -> BsonInt32(tarWinSize),
        "bloatFactor" -> BsonInt32(bloatFactor),
        "augmentationType" -> BsonString(augmentationTypeToString(AugmentationType.Occlusion))
      )
      case TranslationParams(translateFractions, mode, resize, bloatFactor) => Document(
        "translateFractions" -> translateFractions.map(BsonNumber(_)),
        "mode" -> BsonString(mode match {
          case Reflect => "Reflect"
          case Constant => "Constant"
        }),
        "resize" -> BsonBoolean(resize),
        "bloatFactor" -> BsonInt32(bloatFactor),
        "augmentationType" -> BsonString(augmentationTypeToString(AugmentationType.Translation))
      )
      case SaltPepperParams(knockoutFractions, pepperProbability, bloatFactor) => Document(
        "knockoutFractions" -> knockoutFractions.map(BsonNumber(_)),
        "pepperProbability" -> BsonNumber(pepperProbability),
        "bloatFactor" -> BsonInt32(bloatFactor),
        "augmentationType" -> BsonString(augmentationTypeToString(AugmentationType.SaltPepper))
      )
      case MirroringParams(axesFlipped, bloatFactor) => Document(
        "axesFlipped" -> axesFlipped.map {
          case Horizontal => BsonString("Horizontal")
          case Vertical => BsonString("Vertical")
          case Both => BsonString("Both")
        },
        "bloatFactor" -> BsonInt32(bloatFactor),
        "augmentationType" -> BsonString(augmentationTypeToString(AugmentationType.Mirroring))
      )
      case CroppingParams(cropAreaFractions, cpi, resize, bloatFactor) => Document(
        "cropAreaFractions" -> cropAreaFractions.map(BsonNumber(_)),
        "cropsPerImage" -> BsonInt32(cpi),
        "resize" -> BsonBoolean(resize),
        "bloatFactor" -> BsonInt32(bloatFactor),
        "augmentationType" -> BsonString(augmentationTypeToString(AugmentationType.Cropping))
      )
      case BlurringParams(sigmas, bloatFactor) => Document(
        "sigmas" -> sigmas.map(BsonNumber(_)),
        "bloatFactor" -> BsonInt32(bloatFactor),
        "augmentationType" -> BsonString(augmentationTypeToString(AugmentationType.Blurring))
      )
      case PhotometricDistortParams(alphaBounds, delta, bloatFactor) => Document(
        "alphaMin" -> BsonNumber(alphaBounds.min),
        "alphaMax" -> BsonNumber(alphaBounds.max),
        "deltaMax" -> BsonNumber(delta),
        "bloatFactor" -> BsonInt32(bloatFactor),
        "augmentationType" -> BsonString(augmentationTypeToString(AugmentationType.PhotoDistort))
      )
    }

  val AugmentationTypeMap: Map[AugmentationType, String] = Map(
    AugmentationType.Translation -> "translation",
    AugmentationType.Rotation -> "rotation",
    AugmentationType.Shearing -> "shearing",
    AugmentationType.Noising -> "noising",
    AugmentationType.ZoomIn -> "zoomIn",
    AugmentationType.ZoomOut -> "zoomOut",
    AugmentationType.Occlusion -> "occlusion",
    AugmentationType.SaltPepper -> "saltPepper",
    AugmentationType.Mirroring -> "mirroring",
    AugmentationType.Cropping -> "cropping",
    AugmentationType.Blurring -> "blurring",
    AugmentationType.PhotoDistort -> "photometricDistort"
  )

  val AugmentationTypeMapReversed: Map[String, AugmentationType] = AugmentationTypeMap.map(_.swap)

  def augmentationTypeToString(augmentationType: AugmentationType): String = AugmentationTypeMap(augmentationType)

  def stringToAugmentationType(augmentationType: String): AugmentationType =
    AugmentationTypeMapReversed(augmentationType)

}
