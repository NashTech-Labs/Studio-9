package baile.domain.cv.model

import baile.domain.common.ClassReference
import baile.domain.images.AlbumLabelMode

sealed trait CVModelType

object CVModelType {

  case class Custom(
    classReference: ClassReference,
    labelMode: Option[AlbumLabelMode]
  ) extends CVModelType

  case class TL(
    consumer: TLConsumer,
    featureExtractorArchitecture: String
  ) extends CVModelType

  sealed trait TLConsumer {
    val operatorId: String
  }

  object TLConsumer {

    case class Classifier(operatorId: String) extends TLConsumer

    case class Localizer(operatorId: String) extends TLConsumer

    case class Decoder(operatorId: String) extends TLConsumer

  }

}
