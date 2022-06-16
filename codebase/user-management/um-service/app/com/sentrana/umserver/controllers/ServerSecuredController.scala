package com.sentrana.umserver.controllers

import com.sentrana.umserver.entities.ApplicationInfoEntity
import com.sentrana.umserver.services.{ OrganizationQueryService, AuthenticationService, UserGroupQueryService, OrganizationService }
import com.sentrana.umserver.shared.BaseSecuredController
import com.sentrana.umserver.shared.dtos.User

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Paul Lysak on 14.04.16.
 */
trait ServerSecuredController extends BaseSecuredController {
  protected def securedControllerServices: SecuredControllerServices

  override protected def userByToken(token: String): Future[Option[User]] = {
    async {
      if (token.startsWith(AuthenticationService.ClientTokenPrefix))
        await(securedControllerServices.authenticationService.validateApplicationToken(token))
      else {
        val ueOpt = await(securedControllerServices.authenticationService.validateToken(token))
        await(futureOption(ueOpt.map(securedControllerServices.userConverter.toUserDetailDto(_, Map.empty))))
      }
    }
  }

  override protected def rootOrgId: String = securedControllerServices.orgQueryService.rootOrgId
}

