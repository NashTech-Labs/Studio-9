package baile.services.cv.model.util.export.format.v2

private[export] sealed trait CVModelType

private[export] object CVModelType {

  case class TL(
    consumer: TLConsumer,
    featureExtractorReference: ClassReference
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
