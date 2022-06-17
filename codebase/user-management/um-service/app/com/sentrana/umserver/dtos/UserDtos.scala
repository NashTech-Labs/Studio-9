package com.sentrana.umserver.dtos

import com.sentrana.umserver.shared.dtos.enums.UserStatus
import com.sentrana.umserver.shared.dtos.{ DataFilterInstance }
import play.api.libs.json.Json

/**
 * Created by Paul Lysak on 12.04.16.
 */
case class CreateUserRequest(
  username:                 String,
  email:                    String,
  password:                 String,
  firstName:                String,
  lastName:                 String,
  groupIds:                 Set[String],
  dataFilterInstances:      Option[Set[DataFilterInstance]] = None,
  externalId:               Option[String]                  = None,
  status:                   Option[UserStatus]              = None,
  activationCode:           Option[String]                  = None,
  requireEmailConfirmation: Option[Boolean]                 = None
)

object CreateUserRequest {

  import com.sentrana.umserver.JsonFormats._

  implicit val reads = Json.reads[CreateUserRequest]
}

case class UserActivationRequest(
  requireEmailConfirmation: Option[Boolean] = None
)

object UserActivationRequest {

  import com.sentrana.umserver.JsonFormats._

  implicit val reads = Json.reads[UserActivationRequest]
}

case class UserDeactivationRequest(
  sendNotificationEmail: Option[Boolean] = None
)

object UserDeactivationRequest {

  import com.sentrana.umserver.JsonFormats._

  implicit val reads = Json.reads[UserDeactivationRequest]
}

//object UpdateUserAdminRequest {
//  import com.sentrana.umserver.JsonFormats._
//  implicit val reads = Json.reads[UpdateUserAdminRequest]
//}
