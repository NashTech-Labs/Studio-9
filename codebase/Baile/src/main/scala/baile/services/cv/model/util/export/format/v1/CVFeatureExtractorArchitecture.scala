package baile.services.cv.model.util.export.format.v1

import baile.services.cv.model.util.export
import baile.utils.json.EnumFormatBuilder
import play.api.libs.json.Format

private[v1] sealed trait CVFeatureExtractorArchitecture {

  def toClassReference: export.CVModelExportMeta.ClassReference

}

private[v1] object CVFeatureExtractorArchitecture {

  case object VGG16RFB extends CVFeatureExtractorArchitecture {
    override def toClassReference: export.CVModelExportMeta.ClassReference =
      export.CVModelExportMeta.ClassReference(
        moduleName = "ml_lib.feature_extractors.backbones.vgg16_rfb",
        className = "VGG16_RFB",
        packageName = "deepcortex-ml-lib",
        packageVersion = None
      )
  }

  case object VGG16 extends CVFeatureExtractorArchitecture {
    override def toClassReference: export.CVModelExportMeta.ClassReference =
      export.CVModelExportMeta.ClassReference(
        moduleName = "ml_lib.feature_extractors.backbones.vgg16",
        className = "VGG16",
        packageName = "deepcortex-ml-lib",
        packageVersion = None
      )
  }

  case object StackedAutoEncoder extends CVFeatureExtractorArchitecture {
    override def toClassReference: export.CVModelExportMeta.ClassReference =
      export.CVModelExportMeta.ClassReference(
        moduleName = "ml_lib.feature_extractors.backbones.scae",
        className = "StackedAutoEncoder",
        packageName = "deepcortex-ml-lib",
        packageVersion = None
      )
  }

  case object SqueezeNext extends CVFeatureExtractorArchitecture {
    override def toClassReference: export.CVModelExportMeta.ClassReference =
      export.CVModelExportMeta.ClassReference(
        moduleName = "ml_lib.feature_extractors.backbones.squeezenext",
        className = "SqueezeNext",
        packageName = "deepcortex-ml-lib",
        packageVersion = None
      )
  }

  case object SqueezeNextReduced extends CVFeatureExtractorArchitecture {
    override def toClassReference: export.CVModelExportMeta.ClassReference =
      export.CVModelExportMeta.ClassReference(
        moduleName = "ml_lib.feature_extractors.backbones.squeezenext_reduced",
        className = "SqueezeNextReduced",
        packageName = "deepcortex-ml-lib",
        packageVersion = None
      )
  }

  implicit val CVFeatureExtractorArchitectureFormat: Format[CVFeatureExtractorArchitecture] = EnumFormatBuilder.build(
    {
      case "VGG16" => CVFeatureExtractorArchitecture.VGG16
      case "VGG16_RFB" => CVFeatureExtractorArchitecture.VGG16RFB
      case "STACKED_AUTOENCODER" => CVFeatureExtractorArchitecture.StackedAutoEncoder
      case "SQUEEZENEXT_REDUCED" => CVFeatureExtractorArchitecture.SqueezeNextReduced
      case "SQUEEZENEXT" => CVFeatureExtractorArchitecture.SqueezeNext
    },
    {
      case CVFeatureExtractorArchitecture.VGG16 => "VGG16"
      case CVFeatureExtractorArchitecture.VGG16RFB => "VGG16_RFB"
      case CVFeatureExtractorArchitecture.StackedAutoEncoder => "STACKED_AUTOENCODER"
      case CVFeatureExtractorArchitecture.SqueezeNextReduced => "SQUEEZENEXT_REDUCED"
      case CVFeatureExtractorArchitecture.SqueezeNext => "SQUEEZENEXT"
    },
    "CV feature extractor architecture"
  )

}
