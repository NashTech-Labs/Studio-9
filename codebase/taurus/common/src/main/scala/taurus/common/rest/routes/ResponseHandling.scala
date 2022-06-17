package taurus.common.rest.routes

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.server.{ Directives, Route }
import taurus.common.json4s.Json4sSupport
import taurus.common.rest.marshalling.Json4sHttpSupport
import taurus.domain.rest.HttpContract
import taurus.domain.service.DomainObject

trait ResponseHandling {
  self: Directives with Json4sSupport with Json4sHttpSupport =>

  def respond(code: StatusCode): Route = {
    complete(code)
  }

  def respond[From <: DomainObject, To <: HttpContract](value: From)(implicit translator: Translator[From, To]): Route = {
    complete(translator.translate(value))
  }

  def respond[From <: DomainObject, To <: HttpContract](code: StatusCode, value: From)(implicit translator: Translator[From, To]): Route = {
    complete((code, translator.translate(value)))
  }

  def respond[From <: DomainObject, To <: HttpContract](values: Seq[From])(implicit translator: Translator[From, To]): Route = {
    complete(values map translator.translate)
  }
}