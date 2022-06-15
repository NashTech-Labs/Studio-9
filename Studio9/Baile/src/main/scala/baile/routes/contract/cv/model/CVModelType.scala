package baile.routes.contract.cv.model

import baile.domain.cv.model.{ CVModelType => DomainCVModelType }
import baile.domain.images.AlbumLabelMode
import baile.routes.contract.common.ClassReference
import baile.routes.contract.images.AlbumLabelModeFormat
import baile.utils.json.OFormatExtensions._
import io.leonard.TraitFormat.traitFormat
import play.api.libs.functional.syntax._
import play.api.libs.json._

sealed trait CVModelType {

  val labelMode: Option[AlbumLabelMode]

  def toDomain: DomainCVModelType

}

object CVModelType {

  case class TL(
    consumer: TLConsumer,
    architecture: String
  ) extends CVModelType {

    override val labelMode: Option[AlbumLabelMode] = consumer match {
      case TLConsumer.Classifier(_) => Some(AlbumLabelMode.Classification)
      case TLConsumer.Detector(_) => Some(AlbumLabelMode.Localization)
      case TLConsumer.Decoder(_) => None
    }

    override def toDomain: DomainCVModelType.TL =
      DomainCVModelType.TL(
        consumer = consumer.toDomain,
        featureExtractorArchitecture = architecture
      )
  }

  object TL {
    def fromDomain(tl: DomainCVModelType.TL): TL =
      TL(
        architecture = tl.featureExtractorArchitecture,
        consumer = TLConsumer.fromDomain(tl.consumer)
      )

    implicit val TLFormat: OFormat[TL] = (
      __.format[TLConsumer] ~
      (__ \ "architecture").format[String]
    )(TL.apply, unlift(TL.unapply))
      .withField("labelMode", _.labelMode)
  }

  sealed trait TLConsumer {

    def toDomain: DomainCVModelType.TLConsumer
  }

  object TLConsumer {

    case class Classifier(
      classifierType: String
    ) extends TLConsumer {

      override def toDomain: DomainCVModelType.TLConsumer =
        DomainCVModelType.TLConsumer.Classifier(classifierType)
    }

    case class Detector(
      detectorType: String
    ) extends TLConsumer {

      override def toDomain: DomainCVModelType.TLConsumer =
        DomainCVModelType.TLConsumer.Localizer(detectorType)
    }

    case class Decoder(
      decoderType: String
    ) extends TLConsumer {

      override def toDomain: DomainCVModelType.TLConsumer =
        DomainCVModelType.TLConsumer.Decoder(decoderType)
    }

    def fromDomain(tlModelType: DomainCVModelType.TLConsumer): TLConsumer = {
      tlModelType match {
        case DomainCVModelType.TLConsumer.Classifier(classifierType) =>
          Classifier(classifierType)
        case DomainCVModelType.TLConsumer.Localizer(detectorType) =>
          Detector(detectorType)
        case DomainCVModelType.TLConsumer.Decoder(decoderType) =>
          Decoder(decoderType)
      }
    }

    implicit val TLConsumerFormat: Format[TLConsumer] = traitFormat[TLConsumer]("tlType") <<
      ("CLASSIFICATION", Json.format[TLConsumer.Classifier]) <<
      ("LOCALIZATION", Json.format[TLConsumer.Detector]) <<
      ("AUTOENCODER", Json.format[TLConsumer.Decoder])
  }

  case class Custom(
    classReference: ClassReference,
    labelMode: Option[AlbumLabelMode]
  ) extends CVModelType {

    override def toDomain: DomainCVModelType.Custom =
      DomainCVModelType.Custom(
        classReference = classReference.toDomain,
        labelMode = labelMode
      )

  }

  object Custom {

    def fromDomain(modelType: DomainCVModelType.Custom): Custom =
      Custom(
        classReference = ClassReference.fromDomain(modelType.classReference),
        labelMode = modelType.labelMode
      )

    implicit val CustomFormat: OFormat[Custom] = Json.format[Custom]

  }

  def fromDomain(modelType: DomainCVModelType): CVModelType =
    modelType match {
      case tl: DomainCVModelType.TL => CVModelType.TL.fromDomain(tl)
      case custom: DomainCVModelType.Custom => CVModelType.Custom.fromDomain(custom)
    }

  implicit val CVModelTypeFormat: Format[CVModelType] = traitFormat[CVModelType]("type") <<
    ("TL", TL.TLFormat) <<
    ("CUSTOM", Custom.CustomFormat)

}
