package cortex.task.data_augmentation

import cortex.JsonSupport.SnakeJson
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.{ TaskParams, TaskResult }
import play.api.libs.json._

object DataAugmentationParams {

  case class Tag(
      label: String,
      xMin:  Option[Int] = None,
      yMin:  Option[Int] = None,
      xMax:  Option[Int] = None,
      yMax:  Option[Int] = None
  )

  case class ImagesCopyParams(
      inputAlbumPath:  String,
      imagePaths:      Seq[String],
      outputAlbumPath: String,
      s3Params:        S3AccessParams
  ) extends TaskParams

  case class ImagesCopyResult(
      imagePaths: Seq[String]
  ) extends TaskResult

  case class TransformParams(
      inputAlbumPath:     String,
      imagePaths:         Seq[String],
      referenceIds:       Seq[String],
      tags:               Seq[Seq[Tag]],
      s3Params:           S3AccessParams,
      outputAlbumPath:    String,
      augmentationParams: AugmentationParams
  ) extends TaskParams

  case class TransformImagesResult(
      imagePaths:    Seq[String],
      referenceIds:  Seq[String],
      imageSizes:    Seq[Long],
      tags:          Seq[Seq[Tag]],
      augmentations: Seq[AppliedAugmentationInfo]
  )

  case class TransformResult(
      transformImagesResult: TransformImagesResult,
      dataFetchTime:         Long,
      augmentationStartTime: Long,
      augmentationEndTime:   Long
  ) extends TaskResult

  case class AppliedAugmentationInfo(
      name:   AugmentationType,
      arg:    Float,
      resize: Option[Boolean],
      mode:   Option[String],
      extra:  Map[String, Float],

      // specific to SaltPepper
      pepperProbability: Option[Float],

      // specific to Occlusion
      isSarAlbum: Option[Boolean],
      tarWinSize: Option[Int],

      // specific to PhotometricDistortion
      alphaContrast:   Option[Float],
      alphaSaturation: Option[Float],
      deltaHue:        Option[Float]
  )

  sealed trait AugmentationParams {
    def name: AugmentationType

    def bloatFactor: Int
  }

  case class Rotation(
      angles:      Seq[Float],
      resize:      Boolean,
      bloatFactor: Int              = 1,
      name:        AugmentationType = AugmentationType.Rotation
  ) extends AugmentationParams

  case class Shearing(
      angles:      Seq[Float],
      resize:      Boolean,
      bloatFactor: Int              = 1,
      name:        AugmentationType = AugmentationType.Shearing
  ) extends AugmentationParams

  case class Noising(
      noiseSignalRatios: Seq[Float],
      bloatFactor:       Int              = 1,
      name:              AugmentationType = AugmentationType.Noising
  ) extends AugmentationParams

  case class ZoomIn(
      magnifications: Seq[Float],
      resize:         Boolean,
      bloatFactor:    Int              = 1,
      name:           AugmentationType = AugmentationType.ZoomIn
  ) extends AugmentationParams

  case class ZoomOut(
      shrinkFactors: Seq[Float],
      resize:        Boolean,
      bloatFactor:   Int              = 1,
      name:          AugmentationType = AugmentationType.ZoomOut
  ) extends AugmentationParams

  case class Occlusion(
      occAreaFractions: Seq[Float],
      mode:             String,
      isSarAlbum:       Boolean,
      tarWinSize:       Int,
      bloatFactor:      Int              = 1,
      name:             AugmentationType = AugmentationType.Occlusion
  ) extends AugmentationParams

  case class Translation(
      translateFractions: Seq[Float],
      mode:               String,
      resize:             Boolean,
      bloatFactor:        Int              = 1,
      name:               AugmentationType = AugmentationType.Translation
  ) extends AugmentationParams

  case class SaltPepper(
      knockoutFractions: Seq[Float],
      pepperProbability: Float,
      bloatFactor:       Int              = 1,
      name:              AugmentationType = AugmentationType.SaltPepper
  ) extends AugmentationParams

  case class Mirroring(
      axesToFlip:  Seq[String],
      bloatFactor: Int              = 1,
      name:        AugmentationType = AugmentationType.Mirroring
  ) extends AugmentationParams

  case class Cropping(
      cropAreaFractions: Seq[Float],
      cropsPerImage:     Int,
      resize:            Boolean,
      bloatFactor:       Int              = 1,
      name:              AugmentationType = AugmentationType.Cropping
  ) extends AugmentationParams

  case class PhotometricDistort(
      min:         Float,
      max:         Float,
      deltaMax:    Float,
      bloatFactor: Int              = 1,
      name:        AugmentationType = AugmentationType.PhotometricDistort
  ) extends AugmentationParams

  case class Blurring(
      sigmaList:   Seq[Float],
      bloatFactor: Int              = 1,
      name:        AugmentationType = AugmentationType.Blurring
  ) extends AugmentationParams

  sealed trait AugmentationType {
    val requestValue: String
    val responseValue: String
  }

  object AugmentationType {
    case object Rotation extends AugmentationType {
      override val requestValue: String = "Rotation"
      override val responseValue: String = "rotation"
    }
    case object Shearing extends AugmentationType {
      override val requestValue: String = "Shearing"
      override val responseValue: String = "shearing"
    }
    case object Noising extends AugmentationType {
      override val requestValue: String = "Noising"
      override val responseValue: String = "noising"
    }
    case object ZoomIn extends AugmentationType {
      override val requestValue: String = "ZoomIn"
      override val responseValue: String = "zoom_in"
    }
    case object ZoomOut extends AugmentationType {
      override val requestValue: String = "ZoomOut"
      override val responseValue: String = "zoom_out"
    }
    case object Occlusion extends AugmentationType {
      override val requestValue: String = "Occlusion"
      override val responseValue: String = "occlusion"
    }
    case object Translation extends AugmentationType {
      override val requestValue: String = "Translation"
      override val responseValue: String = "translation"
    }
    case object SaltPepper extends AugmentationType {
      override val requestValue: String = "SaltPepper"
      override val responseValue: String = "salt_pepper"
    }
    case object Mirroring extends AugmentationType {
      override val requestValue: String = "Mirroring"
      override val responseValue: String = "mirroring"
    }
    case object Cropping extends AugmentationType {
      override val requestValue: String = "Cropping"
      override val responseValue: String = "cropping"
    }
    case object PhotometricDistort extends AugmentationType {
      override val requestValue: String = "PhotometricDistort"
      override val responseValue: String = "photo_distort"
    }
    case object Blurring extends AugmentationType {
      override val requestValue: String = "Blurring"
      override val responseValue: String = "blurring"
    }
    case object Unchanged extends AugmentationType {
      override val requestValue: String = ""
      override val responseValue: String = "unchanged"
    }
  }

  implicit object AugmentationTypeReads extends Reads[AugmentationType] {
    override def reads(json: JsValue): JsResult[AugmentationType] = json match {
      case JsString(AugmentationType.Rotation.responseValue) => JsSuccess(AugmentationType.Rotation)
      case JsString(AugmentationType.Shearing.responseValue) => JsSuccess(AugmentationType.Shearing)
      case JsString(AugmentationType.Noising.responseValue) => JsSuccess(AugmentationType.Noising)
      case JsString(AugmentationType.ZoomIn.responseValue) => JsSuccess(AugmentationType.ZoomIn)
      case JsString(AugmentationType.ZoomOut.responseValue) => JsSuccess(AugmentationType.ZoomOut)
      case JsString(AugmentationType.Occlusion.responseValue) => JsSuccess(AugmentationType.Occlusion)
      case JsString(AugmentationType.Translation.responseValue) => JsSuccess(AugmentationType.Translation)
      case JsString(AugmentationType.SaltPepper.responseValue) => JsSuccess(AugmentationType.SaltPepper)
      case JsString(AugmentationType.Mirroring.responseValue) => JsSuccess(AugmentationType.Mirroring)
      case JsString(AugmentationType.Cropping.responseValue) => JsSuccess(AugmentationType.Cropping)
      case JsString(AugmentationType.PhotometricDistort.responseValue) => JsSuccess(AugmentationType.PhotometricDistort)
      case JsString(AugmentationType.Blurring.responseValue) => JsSuccess(AugmentationType.Blurring)
      case JsString(AugmentationType.Unchanged.responseValue) => JsSuccess(AugmentationType.Unchanged)
      case _ => JsError(s"Invalid AugmentationType: $json")
    }
  }

  implicit object AugmentationTypeWrites extends Writes[AugmentationType] {
    override def writes(at: AugmentationType): JsValue = SnakeJson.toJson(at.requestValue)
  }

  implicit object AugmentationParamsWrites extends Writes[AugmentationParams] {
    private val rotationWrites = SnakeJson.writes[Rotation]
    private val shearingWrites = SnakeJson.writes[Shearing]
    private val noisingWrites = SnakeJson.writes[Noising]
    private val zoomInWrites = SnakeJson.writes[ZoomIn]
    private val zoomOutWrites = SnakeJson.writes[ZoomOut]
    private val occlusionWrites = SnakeJson.writes[Occlusion]
    private val translationWrites = SnakeJson.writes[Translation]
    private val saltPepperWrites = SnakeJson.writes[SaltPepper]
    private val mirroringWrites = SnakeJson.writes[Mirroring]
    private val croppingWrites = SnakeJson.writes[Cropping]
    private val photometricDistortWrites = SnakeJson.writes[PhotometricDistort]
    private val blurringWrites = SnakeJson.writes[Blurring]

    override def writes(ap: AugmentationParams): JsValue = ap match {
      case rotation: Rotation                     => SnakeJson.toJson(rotation)(rotationWrites)
      case shearing: Shearing                     => SnakeJson.toJson(shearing)(shearingWrites)
      case noising: Noising                       => SnakeJson.toJson(noising)(noisingWrites)
      case zoomIn: ZoomIn                         => SnakeJson.toJson(zoomIn)(zoomInWrites)
      case zoomOut: ZoomOut                       => SnakeJson.toJson(zoomOut)(zoomOutWrites)
      case occlusion: Occlusion                   => SnakeJson.toJson(occlusion)(occlusionWrites)
      case translation: Translation               => SnakeJson.toJson(translation)(translationWrites)
      case saltPepper: SaltPepper                 => SnakeJson.toJson(saltPepper)(saltPepperWrites)
      case mirroring: Mirroring                   => SnakeJson.toJson(mirroring)(mirroringWrites)
      case cropping: Cropping                     => SnakeJson.toJson(cropping)(croppingWrites)
      case photometricDistort: PhotometricDistort => SnakeJson.toJson(photometricDistort)(photometricDistortWrites)
      case blurring: Blurring                     => SnakeJson.toJson(blurring)(blurringWrites)
    }
  }

  implicit val tagReads: Reads[Tag] = SnakeJson.reads[Tag]
  implicit val tagWrites: Writes[Tag] = SnakeJson.writes[Tag]

  implicit val appliedAugmentationInfoReads: Reads[AppliedAugmentationInfo] = SnakeJson.reads[AppliedAugmentationInfo]
  implicit val appliedAugmentationInfoWrites: Writes[AppliedAugmentationInfo] = SnakeJson.writes[AppliedAugmentationInfo]

  implicit val imagesCopyResultReads: Reads[ImagesCopyResult] = SnakeJson.reads[ImagesCopyResult]
  implicit val imagesCopyParamsWrites: Writes[ImagesCopyParams] = SnakeJson.writes[ImagesCopyParams]

  implicit val transformImagesResultReads: Reads[TransformImagesResult] = SnakeJson.reads[TransformImagesResult]
  implicit val transformResultReads: Reads[TransformResult] = SnakeJson.reads[TransformResult]
  implicit val transformParamsWrites: Writes[TransformParams] = SnakeJson.writes[TransformParams]
}
