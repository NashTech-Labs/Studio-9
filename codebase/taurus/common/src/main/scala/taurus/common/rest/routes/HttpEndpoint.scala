package taurus.common.rest.routes

import java.util.UUID

import akka.http.scaladsl.server.{ Directives, Route }
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.util.Timeout
import taurus.common.json4s.Json4sSupport
import taurus.common.rest.BaseConfig
import taurus.common.rest.marshalling.Json4sHttpSupport
import taurus.domain.rest.HttpContract
import taurus.domain.service.DomainObject

trait HttpEndpoint extends Directives
    with ResponseHandling
    with Json4sSupport
    with Json4sHttpSupport
    with CustomUnmarshallers
    with ImplicitTranslations
    with ImplicitAskTimeout {

  def routes: Route
}

trait CustomUnmarshallers {
  implicit val uuidUnmarshaller: Unmarshaller[String, UUID] = Unmarshaller.strict[String, UUID] { value =>
    UUID.fromString(value)
  }
}

trait ImplicitTranslations {
  implicit def toDomainObject[R <: HttpContract, D <: DomainObject](representation: R)(implicit translator: Translator[R, D]): D = {
    translator.translate(representation)
  }
}

trait ImplicitAskTimeout {
  val config: BaseConfig
  implicit val timeout: Timeout = config.httpConfig.requestTimeout
}