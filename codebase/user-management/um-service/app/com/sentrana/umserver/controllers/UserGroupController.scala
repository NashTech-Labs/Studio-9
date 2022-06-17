package com.sentrana.umserver.controllers

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.controllers.util.JsonObjectParser
import com.sentrana.umserver.dtos.{ CreateUserGroupRequest, UpdateUserGroupRequest }
import com.sentrana.umserver.services._
import com.sentrana.umserver.shared.dtos.UserGroup
import play.api.Configuration
import play.api.libs.json.{ JsValue, Json, Reads }

/**
 * Created by Paul Lysak on 18.04.16.
 */
@Singleton
class UserGroupController @Inject() (
    userGroupCommandService: UserGroupService,
    userGroupQueryService:   UserGroupQueryService,
    //    protected val authenticationService: AuthenticationService,
    //    val orgQueryService:                 OrganizationQueryService
    val securedControllerServices: SecuredControllerServices
) extends EntityMTCrudController[CreateUserGroupRequest, UpdateUserGroupRequest, UserGroup] {

  import com.sentrana.umserver.JsonFormats._

  override def entityCrudService = userGroupCommandService

  override def entityQueryService = userGroupQueryService

  override protected def entityToDtoJson(entity: UserGroup): JsValue = Json.toJson(entity)

  override protected def permissionPrefix: String = "GROUPS"

  override protected implicit def updReqReads: Reads[UpdateUserGroupRequest] = UpdateUserGroupRequest.reads

  override protected implicit def createReqReads: Reads[CreateUserGroupRequest] = CreateUserGroupRequest.reads

  override protected def entityName: String = "group"
}
