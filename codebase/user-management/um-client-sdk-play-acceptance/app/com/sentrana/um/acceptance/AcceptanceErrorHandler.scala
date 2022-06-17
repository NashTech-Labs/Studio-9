package com.sentrana.um.acceptance

import javax.inject.{ Provider, Inject }

import com.sentrana.um.acceptance.exceptions.AcceptanceItemNotFoundException
import com.sentrana.umserver.shared.dtos.GenericResponse
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.{ Result, RequestHeader }
import play.api.routing.Router
import play.api.{ OptionalSourceMapper, Configuration, Environment }

import scala.concurrent.Future

/**
 * Created by Paul Lysak on 02.05.16.
 */
class AcceptanceErrorHandler @Inject() (
    env:          Environment,
    config:       Configuration,
    sourceMapper: OptionalSourceMapper,
    router:       Provider[Router]
) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {
  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    exception match {
      case e: AcceptanceItemNotFoundException => GenericResponse(e.getMessage).notFoundF
      case _                                  => super.onServerError(request, exception)
    }
  }
}
