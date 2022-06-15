package baile.services.cv.model.util.export

import baile.domain.cv.model._
import baile.services.cv.model.util.export.CVModelExportMeta.{ AlbumLabelMode, CVModelType, ClassReference }
import play.api.libs.json._

private[cv] case class CVModelExportMeta(
  name: Option[String],
  modelType: CVModelType,
  description: Option[String],
  classNames: Option[Seq[String]]
) {

  def toContract: format.v2.CVModelExportMeta = {

    def convertClassReference(classReference: ClassReference): format.v2.ClassReference =
      format.v2.ClassReference(
        className = classReference.className,
        moduleName = classReference.moduleName,
        packageName = classReference.packageName,
        packageVersion = classReference.packageVersion.map { version =>
          format.v2.Version(version.major, version.minor, version.patch, version.suffix)
        }
      )

    def convertAlbumLabelMode(labelMode: AlbumLabelMode): format.v2.AlbumLabelMode =
      labelMode match {
        case AlbumLabelMode.Classification => format.v2.AlbumLabelMode.Classification
        case AlbumLabelMode.Localization => format.v2.AlbumLabelMode.Localization
      }

    def convertTLConsumer(consumer: CVModelType.TLConsumer): format.v2.CVModelType.TLConsumer =
      consumer match {
        case CVModelType.TLConsumer.Classifier(reference) =>
          format.v2.CVModelType.TLConsumer.Classifier(
            classReference = convertClassReference(reference)
          )
        case CVModelType.TLConsumer.Localizer(reference) =>
          format.v2.CVModelType.TLConsumer.Localizer(
            classReference = convertClassReference(reference)
          )
        case CVModelType.TLConsumer.Decoder(reference) =>
          format.v2.CVModelType.TLConsumer.Decoder(
            classReference = convertClassReference(reference)
          )
      }

    def convertCVModelType(cvModelType: CVModelType): format.v2.CVModelType =
      cvModelType match {
        case CVModelType.TL(consumer, feReference, _) =>
          format.v2.CVModelType.TL(
            convertTLConsumer(consumer),
            convertClassReference(feReference)
          )
        case CVModelType.Custom(reference, labelMode) =>
          format.v2.CVModelType.Custom(
            classReference = convertClassReference(reference),
            labelMode = labelMode.map(convertAlbumLabelMode)
          )
      }

    format.v2.CVModelExportMeta(
      name = name,
      modelType = convertCVModelType(modelType),
      description = description,
      classNames = classNames
    )

  }

}

object CVModelExportMeta {

  implicit val MetaReads: Reads[CVModelExportMeta] = {

    def versionReads(obj: JsObject): Reads[Either[String, CVModelExportMeta]] =
      (obj \ "__version").asOpt[String] match {
        case Some(version) => version match {
          case "2" => format.v2.CVModelExportMeta.CVModelExportJsonFormat.map(_.toDomain)
          case unknown => Reads(_ => JsError(s"Unknown meta version: $unknown"))
        }
        case None => // Initial version
          if (obj.value.isDefinedAt("modelType")) {
            format.v1.CVModelExportMeta.CVModelExportJsonFormat.map(_.toDomain)
          } else {
            format.v1.CVFeatureExtractorExportMeta.CVFeatureExtractorExportMetaFormat.map(_.toDomain)
          }
      }

    for {
      obj <- Reads.of[JsObject]
      eitherResult <- versionReads(obj)
      result <- eitherResult match {
        case Left(error) => Reads(_ => JsError(error))
        case Right(value) => Reads.pure(value)
      }
    } yield result
  }

  implicit val MetaWrites: OWrites[CVModelExportMeta] = OWrites { meta =>
    Json.toJsObject(meta.toContract) + ("__version" -> JsString("2"))
  }

  def apply(
    model: CVModel,
    modelType: CVModelType
  ): CVModelExportMeta = CVModelExportMeta(
    name = Some(model.name),
    modelType = modelType,
    description = model.description,
    classNames = model.classNames
  )

  private[cv] case class Version(major: Int, minor: Int, patch: Int, suffix: Option[String])

  private[cv] case class ClassReference(
    className: String,
    moduleName: String,
    packageName: String,
    packageVersion: Option[Version]
  )

  private[cv] sealed trait CVModelType

  private[cv] object CVModelType {

    case class TL(
      consumer: TLConsumer,
      featureExtractorReference: ClassReference,
      featureExtractorOnly: Boolean = false
    ) extends CVModelType

    sealed trait TLConsumer {
      val classReference: ClassReference
    }

    object TLConsumer {

      case class Classifier(
        classReference: ClassReference
      ) extends TLConsumer

      case class Localizer(
        classReference: ClassReference
      ) extends TLConsumer

      case class Decoder(
        classReference: ClassReference
      ) extends TLConsumer

    }

    case class Custom(
      classReference: ClassReference,
      labelMode: Option[AlbumLabelMode]
    ) extends CVModelType

  }

  private[cv] sealed trait AlbumLabelMode

  private[cv] object AlbumLabelMode {

    case object Classification extends AlbumLabelMode

    case object Localization extends AlbumLabelMode

  }


}
