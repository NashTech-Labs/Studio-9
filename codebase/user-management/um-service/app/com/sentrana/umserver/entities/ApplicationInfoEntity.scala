package com.sentrana.umserver.entities

import java.time.ZonedDateTime

import com.sentrana.umserver.shared.dtos.{ ApplicationInfo, WithId }
import org.apache.commons.lang3.RandomStringUtils
import org.mongodb.scala.bson.collection.immutable.Document
import play.api.libs.json.Json

import scala.concurrent.Future

/**
 * Created by Alexander on 28.04.2016.
 */
/**
 *  Information about an Application. Typically, this is used to display information about an Application.
 *
 * @param id
 * @param name
 * @param desc
 * @param url
 * @param clientSecret
 * @param created
 * @param updated
 */
case class ApplicationInfoEntity(
    id:                   String,
    name:                 String,
    desc:                 Option[String],
    url:                  Option[String],
    clientSecret:         String,
    passwordResetUrl:     Option[String] = None,
    emailConfirmationUrl: Option[String] = None,
    created:              ZonedDateTime  = ZonedDateTime.now(),
    updated:              ZonedDateTime  = ZonedDateTime.now()
) extends WithId {

  def toApplicationInfoDto(): ApplicationInfo =
    com.sentrana.umserver.shared.dtos.ApplicationInfo(
      id                   = id,
      name                 = name,
      desc                 = desc,
      url                  = url,
      passwordResetUrl     = passwordResetUrl,
      emailConfirmationUrl = emailConfirmationUrl,
      created              = created,
      updated              = updated
    )
}

object ApplicationInfoEntity {
  implicit val applicationInfoEntityFormat = Json.format[ApplicationInfoEntity]
}
