package baile.services.images

import baile.domain.images.augmentation._
import com.typesafe.config.Config

import scala.collection.JavaConverters._

class DefaultAugmentationValuesService(protected val conf: Config) {

  private val defaultRotationParams = RotationParams(
    angles = conf.getDoubleList("rotation.angles").asScala.map(_.toFloat),
    resize = conf.getBoolean("rotation.resize"),
    bloatFactor = conf.getInt("rotation.bloat-factor")
  )

  private val defaultShearingParams = ShearingParams(
    angles = conf.getDoubleList("shearing.angles").asScala.map(_.toFloat),
    resize = conf.getBoolean("shearing.resize"),
    bloatFactor = conf.getInt("shearing.bloat-factor")
  )

  private val defaultNoisingParams = NoisingParams(
    noiseSignalRatios = conf.getDoubleList("noising.noise-signal-ratio").asScala
      .map(_.toFloat),
    bloatFactor = conf.getInt("noising.bloat-factor")
  )

  private val defaultZoomInParams = ZoomInParams(
    magnifications = conf.getDoubleList("zoomin.magnifications").asScala
      .map(_.toFloat),
    resize = conf.getBoolean("zoomin.resize"),
    bloatFactor = conf.getInt("zoomin.bloat-factor")
  )

  private val defaultZoomOutParams = ZoomOutParams(
    shrinkFactors = conf.getDoubleList("zoomout.shrink-factors").asScala
      .map(_.toFloat),
    resize = conf.getBoolean("zoomout.resize"),
    bloatFactor = conf.getInt("zoomout.bloat-factor")
  )

  private val defaultOcclusionParams = OcclusionParams(
    occAreaFractions = conf.getDoubleList("occlusion.occ-area-fractions").asScala
      .map(_.toFloat),
    mode = conf.getString("occlusion.mode") match {
      case "zero" => OcclusionMode.Zero
      case "background" => OcclusionMode.Background
    },
    isSarAlbum = conf.getBoolean("occlusion.is-sar-album"),
    tarWinSize = conf.getInt("occlusion.tar-win-size"),
    bloatFactor = conf.getInt("occlusion.bloat-factor")
  )

  private val defaultTranslationParams = TranslationParams(
    translateFractions = conf.getDoubleList("translation.translate-fractions").asScala
      .map(_.toFloat),
    mode = conf.getString("translation.mode") match {
      case "constant" => TranslationMode.Constant
      case "reflect" => TranslationMode.Reflect
    },
    resize = conf.getBoolean("translation.resize"),
    bloatFactor = conf.getInt("translation.bloat-factor")
  )

  private val defaultMirroringParams = MirroringParams(
    axesToFlip = conf.getStringList("mirroring.flip-axes").asScala. map {
      case "horizontal" => MirroringAxisToFlip.Horizontal
      case "vertical" => MirroringAxisToFlip.Vertical
      case "both" => MirroringAxisToFlip.Both
    },
    bloatFactor = conf.getInt("mirroring.bloat-factor")
  )

  private val defaultBlurringParams = BlurringParams(
    sigmaList = conf.getDoubleList("blurring.sigma-list").asScala.map(_.toFloat),
    bloatFactor = conf.getInt("blurring.bloat-factor")
  )

  private val defaultSaltPepperParams = SaltPepperParams(
    knockoutFractions = conf.getDoubleList("salt-pepper.knockout-fractions").asScala
      .map(_.toFloat),
    pepperProbability = conf.getDouble("salt-pepper.pepper-probability").toFloat,
    bloatFactor = conf.getInt("mirroring.bloat-factor")

  )

  private val defaultPhotometricDistortParams = PhotometricDistortParams(
    PhotometricDistortAlphaBounds(
      min = conf.getDouble("photometric-distort.alpha-bounds.min").toFloat,
      max = conf.getDouble("photometric-distort.alpha-bounds.max").toFloat
    ),
    deltaMax = conf.getInt("photometric-distort.delta-max"),
    bloatFactor = conf.getInt("mirroring.bloat-factor")
  )

  private val defaultCroppingParams = CroppingParams(
    cropAreaFractions = conf.getDoubleList("cropping.crop-area-fractions").asScala
      .map(_.toFloat),
    cropsPerImage = conf.getInt("cropping.crops-per-image"),
    resize = conf.getBoolean("cropping.resize"),
    bloatFactor = conf.getInt("cropping.bloat-factor")
  )

  val getDefaultAugmentationValues: Seq[AugmentationParams] = {
    Seq(
      defaultRotationParams,
      defaultShearingParams,
      defaultCroppingParams,
      defaultBlurringParams,
      defaultNoisingParams,
      defaultZoomInParams,
      defaultZoomOutParams,
      defaultOcclusionParams,
      defaultSaltPepperParams,
      defaultMirroringParams,
      defaultTranslationParams,
      defaultPhotometricDistortParams
    )
  }

}
