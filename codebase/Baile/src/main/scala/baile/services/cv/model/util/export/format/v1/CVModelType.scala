package baile.services.cv.model.util.export.format.v1

import baile.services.cv.model.util.export
import baile.utils.json.EnumFormatBuilder
import play.api.libs.json._

private[v1] sealed trait CVModelType {
  val labelMode: AlbumLabelMode

  def toTlConsumer: export.CVModelExportMeta.CVModelType.TLConsumer

}

private[v1] object CVModelType {

  sealed trait Classifier extends CVModelType {
    val labelMode = AlbumLabelMode.Classification
  }

  object Classifier {

    case object FCN1Layer extends Classifier {
      override def toTlConsumer: export.CVModelExportMeta.CVModelType.TLConsumer =
        export.CVModelExportMeta.CVModelType.TLConsumer.Classifier(
          export.CVModelExportMeta.ClassReference(
            moduleName = "ml_lib.classifiers.fcn.fcn1",
            className = "FCN1",
            packageName = "deepcortex-ml-lib",
            packageVersion = None
          )
        )
    }

    case object FCN2Layer extends Classifier {
      override def toTlConsumer: export.CVModelExportMeta.CVModelType.TLConsumer =
        export.CVModelExportMeta.CVModelType.TLConsumer.Classifier(
          export.CVModelExportMeta.ClassReference(
            moduleName = "ml_lib.classifiers.fcn.fcn2",
            className = "FCN2",
            packageName = "deepcortex-ml-lib",
            packageVersion = None
          )
        )
    }

    case object FCN3Layer extends Classifier {
      override def toTlConsumer: export.CVModelExportMeta.CVModelType.TLConsumer =
        export.CVModelExportMeta.CVModelType.TLConsumer.Classifier(
          export.CVModelExportMeta.ClassReference(
            moduleName = "ml_lib.classifiers.fcn.fcn3",
            className = "FCN3",
            packageName = "deepcortex-ml-lib",
            packageVersion = None
          )
        )
    }

    case object KPCAMNL extends Classifier {
      override def toTlConsumer: export.CVModelExportMeta.CVModelType.TLConsumer =
        export.CVModelExportMeta.CVModelType.TLConsumer.Classifier(
          export.CVModelExportMeta.ClassReference(
            moduleName = "ml_lib.classifiers.kpca_mnl.models.kpca_mnl",
            className = "KPCA_MNL",
            packageName = "deepcortex-ml-lib",
            packageVersion = None
          )
        )
    }

    case object RPCAMNL extends Classifier {
      override def toTlConsumer: export.CVModelExportMeta.CVModelType.TLConsumer =
        export.CVModelExportMeta.CVModelType.TLConsumer.Classifier(
          export.CVModelExportMeta.ClassReference(
            moduleName = "ml_lib.classifiers.kpca_mnl.models.rpca_mnl",
            className = "RPCA_MNL",
            packageName = "deepcortex-ml-lib",
            packageVersion = None
          )
        )
    }

    case object FREESCALE extends Classifier {
      override def toTlConsumer: export.CVModelExportMeta.CVModelType.TLConsumer =
        export.CVModelExportMeta.CVModelType.TLConsumer.Classifier(
          export.CVModelExportMeta.ClassReference(
            moduleName = "ml_lib.classifiers.free_scale.free_scale",
            className = "FreeScale",
            packageName = "deepcortex-ml-lib",
            packageVersion = None
          )
        )
    }

  }

  sealed trait Localizer extends CVModelType {
    val labelMode = AlbumLabelMode.Localization
  }

  object Localizer {

    case object RFBNet extends Localizer {
      override def toTlConsumer: export.CVModelExportMeta.CVModelType.TLConsumer =
        export.CVModelExportMeta.CVModelType.TLConsumer.Localizer(
          export.CVModelExportMeta.ClassReference(
            moduleName = "ml_lib.detectors.rfb_detector.RFBDetector",
            className = "RFBDetector",
            packageName = "deepcortex-ml-lib",
            packageVersion = None
          )
        )
    }

  }

  implicit val AlbumLabelModeFormat: Format[AlbumLabelMode] = EnumFormatBuilder.build(
    {
      case "CLASSIFICATION" => AlbumLabelMode.Classification
      case "LOCALIZATION" => AlbumLabelMode.Localization
    },
    {
      case AlbumLabelMode.Classification => "CLASSIFICATION"
      case AlbumLabelMode.Localization => "LOCALIZATION"
    },
    "album label mode"
  )

  implicit val CVModelClassifierTypeFormat: Format[CVModelType.Classifier] = EnumFormatBuilder.build(
    {
      case "FCN_1LAYER" => CVModelType.Classifier.FCN1Layer
      case "FCN_2LAYER" => CVModelType.Classifier.FCN2Layer
      case "FCN_3LAYER" => CVModelType.Classifier.FCN3Layer
      case "KPCA_MNL" => CVModelType.Classifier.KPCAMNL
      case "RPCA_MNL" => CVModelType.Classifier.RPCAMNL
      case "FREESCALE" => CVModelType.Classifier.FREESCALE
    },
    {
      case CVModelType.Classifier.FCN1Layer => "FCN_1LAYER"
      case CVModelType.Classifier.FCN2Layer => "FCN_2LAYER"
      case CVModelType.Classifier.FCN3Layer => "FCN_3LAYER"
      case CVModelType.Classifier.KPCAMNL => "KPCA_MNL"
      case CVModelType.Classifier.RPCAMNL => "RPCA_MNL"
      case CVModelType.Classifier.FREESCALE => "FREESCALE"
    },
    "cv model classifier type"
  )

  implicit val CVModelLocalizerTypeFormat: Format[CVModelType.Localizer] = EnumFormatBuilder.build(
    {
      case "RFBNET" => CVModelType.Localizer.RFBNet
    },
    {
      case CVModelType.Localizer.RFBNet => "RFBNET"
    },
    "cv model localizer type"
  )

  implicit val CVModelTypeFormat: Format[CVModelType] = Format[CVModelType](
    for {
      labelMode <- (__ \ "labelMode").read[AlbumLabelMode]
      nameField = __ \ "name"
      modelType <- labelMode match {
        case AlbumLabelMode.Classification => nameField.read[CVModelType.Classifier]
        case AlbumLabelMode.Localization => nameField.read[CVModelType.Localizer]
      }
    } yield modelType,

    Writes[CVModelType] { modelType =>
      val childJson: JsValue = modelType match {
        case classifier: CVModelType.Classifier => CVModelClassifierTypeFormat.writes(classifier)
        case localizer: CVModelType.Localizer => CVModelLocalizerTypeFormat.writes(localizer)
      }
      Json.obj(
        "name" -> childJson,
        "labelMode" -> AlbumLabelModeFormat.writes(modelType.labelMode)
      )
    }
  )

}

