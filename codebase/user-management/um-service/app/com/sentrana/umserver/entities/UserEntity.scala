package com.sentrana.umserver.entities

import java.time.ZonedDateTime

import com.sentrana.umserver.services.{ OrganizationQueryService, UserGroupQueryService, OrganizationService }
import com.sentrana.umserver.shared.dtos.{ DataFilterInstance, User, WithId }
import com.sentrana.umserver.shared.dtos.enums.UserStatus
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * User format in MongoDB differs too much from DTO, therefore a separate entity class needed.
 *
 * Created by Paul Lysak on 12.04.16.
 */
case class UserEntity(
    id:                  String,
    username:            String,
    email:               String,
    password:            String,
    firstName:           String,
    lastName:            String,
    status:              UserStatus,
    created:             ZonedDateTime           = ZonedDateTime.now(),
    updated:             ZonedDateTime           = ZonedDateTime.now(),
    dataFilterInstances: Set[DataFilterInstance],
    groupIds:            Set[String],
    organizationId:      String,
    externalId:          Option[String]          = None,
    activationCode:      Option[String]          = None
) extends WithId {

}

object UserEntity {
  import com.sentrana.umserver.JsonFormats._

  implicit val userFormat = Json.format[UserEntity]
}

