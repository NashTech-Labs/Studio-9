package com.sentrana.umserver

import javax.inject.{ Inject, Provider }

import com.sentrana.umserver.exceptions._
import com.sentrana.umserver.shared.dtos.GenericResponse
import play.api.http.DefaultHttpErrorHandler
import play.api.mvc.{ RequestHeader, Result }
import play.api.routing.Router
import play.api.{ Configuration, Environment, OptionalSourceMapper }

import scala.concurrent.Future

/**
 * Created by Paul Lysak on 19.04.16.
 */
class UmServerErrorHandler @Inject() (
    env:          Environment,
    config:       Configuration,
    sourceMapper: OptionalSourceMapper,
    router:       Provider[Router]
) extends DefaultHttpErrorHandler(env, config, sourceMapper, router) {
  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    exception match {
      case e @ (_: ValidationException | _: DataRetrievalException) => GenericResponse(e.getMessage).badRequestF
      case e: ItemNotFoundException => GenericResponse(e.getMessage).notFoundF
      case e: AuthenticationException => GenericResponse(e.getMessage).unauthorizedF
      case e: AccessDeniedException => GenericResponse(e.getMessage).forbiddenF
      case e: TooManyRequestsException => GenericResponse(e.getMessage).tooManyRequestF
      case _ => super.onServerError(request, exception)
    }
  }

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    if (request.contentType.contains("application/json")) {
      val m = Option(message).filter(_.nonEmpty).getOrElse(s"client error $statusCode")
      Future.successful(GenericResponse(m).status(statusCode))
    }
    else super.onClientError(request, statusCode, message)
  }
}
