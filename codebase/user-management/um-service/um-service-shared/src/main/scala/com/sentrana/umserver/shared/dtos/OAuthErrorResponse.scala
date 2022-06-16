package com.sentrana.umserver.shared.dtos

import play.api.libs.json._
import play.api.mvc.Results

import scala.concurrent.Future
/**
 * Created by Alexander on 26.07.2016.
 */
case class OAuthErrorResponse(error: String, error_description: Option[String] = None) {
  import OAuthErrorResponse._

  def result = Results.BadRequest(Json.toJson(this))

  def resultF = Future.successful(result)
}

object OAuthErrorResponse {
  private val INVALID_REQUEST = "invalid_request"
  private val INVALID_GRANT = "invalid_grant"
  private val UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type"

  def invalidRequest(errorDescription: Option[String] = None): OAuthErrorResponse = {
    OAuthErrorResponse(INVALID_REQUEST, errorDescription)
  }

  def invalidGrant(errorDescription: Option[String] = None): OAuthErrorResponse = {
    OAuthErrorResponse(INVALID_GRANT, errorDescription)
  }

  def unsupportedGrantType(errorDescription: Option[String] = None): OAuthErrorResponse = {
    OAuthErrorResponse(UNSUPPORTED_GRANT_TYPE, errorDescription)
  }

  implicit val oAuthErrorResponseWrites: Writes[OAuthErrorResponse] = Json.writes[OAuthErrorResponse]
}
