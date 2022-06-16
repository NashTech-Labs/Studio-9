package com.sentrana.umserver.dtos

import com.sentrana.umserver.shared.dtos.{ DataFilterInstance, Permission }
import play.api.libs.json.Json

/**
 * Created by Paul Lysak on 18.04.16.
 */
case class CreateUserGroupRequest(
  parentGroupId:       Option[String],
  name:                String,
  desc:                Option[String],
  grantsPermissions:   Set[Permission],
  forChildOrgs:        Boolean                         = false,
  dataFilterInstances: Option[Set[DataFilterInstance]]
)

object CreateUserGroupRequest {
  import com.sentrana.umserver.JsonFormats._

  implicit val reads = Json.reads[CreateUserGroupRequest]
}

case class UpdateUserGroupRequest(
  parentGroupId:       Option[String]                  = None,
  resetParentGroupId:  Option[Boolean]                 = None,
  name:                Option[String]                  = None,
  desc:                Option[String]                  = None,
  grantsPermissions:   Option[Set[Permission]]         = None,
  forChildOrgs:        Option[Boolean]                 = None,
  dataFilterInstances: Option[Set[DataFilterInstance]] = None
)

object UpdateUserGroupRequest {
  import com.sentrana.umserver.JsonFormats._

  implicit val reads = Json.reads[UpdateUserGroupRequest]
}

