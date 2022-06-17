package com.sentrana.umserver.dtos

import play.api.libs.json.Json

/**
 * Created by Alexander on 28.04.2016.
 */
case class CreateApplicationInfoRequest(
  name:                 String,
  desc:                 Option[String],
  url:                  Option[String],
  passwordResetUrl:     Option[String] = None,
  emailConfirmationUrl: Option[String] = None
)

object CreateApplicationInfoRequest {

  implicit val reads = Json.reads[CreateApplicationInfoRequest]
}

case class UpdateApplicationInfoRequest(
  name:                 Option[String] = None,
  desc:                 Option[String] = None,
  url:                  Option[String] = None,
  passwordResetUrl:     Option[String] = None,
  emailConfirmationUrl: Option[String] = None
)

object UpdateApplicationInfoRequest {

  implicit val reads = Json.reads[UpdateApplicationInfoRequest]
}
