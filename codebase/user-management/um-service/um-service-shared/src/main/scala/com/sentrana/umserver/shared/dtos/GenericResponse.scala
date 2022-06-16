package com.sentrana.umserver.shared.dtos

import play.api.libs.json._
import play.api.mvc.Results

import scala.concurrent.Future

/**
 * Created by Paul Lysak on 12.04.16.
 */
object GenericResponse {
  implicit val writes = Json.writes[GenericResponse]
}

case class GenericResponse(message: String, id: Option[String] = None) {
  def toJson = Json.toJson(this)

  def status(code: Int) = Results.Status(code)(Json.toJson(this))

  def ok = Results.Ok(Json.toJson(this))

  def okF = Future.successful(ok)

  def badRequest = Results.BadRequest(Json.toJson(this))

  def badRequestF = Future.successful(badRequest)

  def unauthorized = Results.Unauthorized(Json.toJson(this))

  def unauthorizedF = Future.successful(unauthorized)

  def forbidden = Results.Forbidden(Json.toJson(this))

  def forbiddenF = Future.successful(forbidden)

  def notFound = Results.NotFound(Json.toJson(this))

  def notFoundF = Future.successful(notFound)

  def tooManyRequest = Results.TooManyRequest(Json.toJson(this))

  def tooManyRequestF = Future.successful(tooManyRequest)
}

