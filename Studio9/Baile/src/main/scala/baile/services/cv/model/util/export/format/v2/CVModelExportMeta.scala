package baile.services.cv.model.util.export.format.v2

import baile.services.cv.model.util.export
import baile.utils.json.EnumFormatBuilder
import cats.implicits._
import io.leonard.TraitFormat.traitFormat
import play.api.libs.json._
import play.api.libs.functional.syntax._

private[export] case class CVModelExportMeta(
  name: Option[String],
  modelType: CVModelType,
  description: Option[String],
  classNames: Option[Seq[String]]
) {

  def toDomain: Either[String, export.CVModelExportMeta] = {

    def convertClassReference(classReference: ClassReference): export.CVModelExportMeta.ClassReference =
      export.CVModelExportMeta.ClassReference(
        className = classReference.className,
        moduleName = classReference.moduleName,
        packageName = classReference.packageName,
        packageVersion = classReference.packageVersion.map { version =>
          export.CVModelExportMeta.Version(version.major, version.minor, version.patch, version.suffix)
        }
      )

    def convertAlbumLabelMode(labelMode: AlbumLabelMode): export.CVModelExportMeta.AlbumLabelMode =
      labelMode match {
        case AlbumLabelMode.Classification => export.CVModelExportMeta.AlbumLabelMode.Classification
        case AlbumLabelMode.Localization => export.CVModelExportMeta.AlbumLabelMode.Localization
      }

    def convertTLConsumer(consumer: CVModelType.TLConsumer): export.CVModelExportMeta.CVModelType.TLConsumer =
      consumer match {
        case CVModelType.TLConsumer.Classifier(reference) =>
          export.CVModelExportMeta.CVModelType.TLConsumer.Classifier(
            classReference = convertClassReference(reference)
          )
        case CVModelType.TLConsumer.Localizer(reference) =>
          export.CVModelExportMeta.CVModelType.TLConsumer.Localizer(
            classReference = convertClassReference(reference)
          )
        case CVModelType.TLConsumer.Decoder(reference) =>
          export.CVModelExportMeta.CVModelType.TLConsumer.Decoder(
            classReference = convertClassReference(reference)
          )
      }

    def convertCVModelType(cvModelType: CVModelType): export.CVModelExportMeta.CVModelType =
      cvModelType match {
        case CVModelType.TL(consumer, feReference) =>
          export.CVModelExportMeta.CVModelType.TL(
            convertTLConsumer(consumer),
            convertClassReference(feReference)
          )
        case CVModelType.Custom(reference, labelMode) =>
          export.CVModelExportMeta.CVModelType.Custom(
            classReference = convertClassReference(reference),
            labelMode = labelMode.map(convertAlbumLabelMode)
          )
      }

    export.CVModelExportMeta(
      name = name,
      modelType = convertCVModelType(modelType),
      description = description,
      classNames = classNames
    ).asRight
  }
}

private[export] object CVModelExportMeta {

  implicit val VersionFormat: Format[Version] = {
    val reads = for {
      str <- Reads.of[String]
      regex = """^([0-9]+)\.([0-9]+)\.([0-9]+)(?:\.(.+))?$""".r
      result <- str match {
        case regex(major, minor, patch, suffix) =>
          Reads.pure(Version(major.toInt, minor.toInt, patch.toInt, Option(suffix)))
        case _ => Reads[Version](_ => JsError("Invalid version format"))
      }
    } yield result

    val writes = Writes.of[String].contramap[Version] { case Version(major, minor, patch, suffix) =>
      s"$major.$minor.$patch" + suffix.fold("")("." + _)
    }
    Format(reads, writes)
  }

  implicit val ClassReferenceFormat: OFormat[ClassReference] = Json.format[ClassReference]

  implicit val TLConsumerTypeFormat: Format[CVModelType.TLConsumer] =
    traitFormat[CVModelType.TLConsumer]("tlType") <<
      ("CLASSIFIER", Json.format[CVModelType.TLConsumer.Classifier]) <<
      ("LOCALIZER", Json.format[CVModelType.TLConsumer.Localizer]) <<
      ("DECODER", Json.format[CVModelType.TLConsumer.Decoder])

  implicit val TLModelTypeFormat: Format[CVModelType.TL] = Json.format[CVModelType.TL]

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

  implicit val CustomModelTypeFormat: OFormat[CVModelType.Custom] = Json.format[CVModelType.Custom]

  implicit val CVModelTypeFormat: Format[CVModelType] =
    traitFormat[CVModelType]("__type") <<
      ("TL", TLModelTypeFormat) <<
      ("CUSTOM", CustomModelTypeFormat)

  implicit val CVModelExportJsonFormat: OFormat[CVModelExportMeta] = Json.format[CVModelExportMeta]

}
