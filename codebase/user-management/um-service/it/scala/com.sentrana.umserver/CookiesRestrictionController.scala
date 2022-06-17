package com.sentrana.umserver

import com.sentrana.umserver.shared.BaseSecuredController
import com.sentrana.umserver.shared.dtos.{Organization, User}

import scala.concurrent.Future

/**
  * Created by Alexander on 04.08.2016.
  */
class CookiesRestrictionController(organization: Organization, userDto: User) extends BaseSecuredController() {

  override protected def userByToken(token: String): Future[Option[User]] = Future.successful(Option(userDto))

  override protected def rootOrgId: String = organization.id

  def methodWithDefaultCookiesRestriction() = SecuredAction.async {
    Future.successful(Ok)
  }

  def methodWithEnabledCookies() = SecuredAction(authCookieEnabled = Some(true)).async {
    Future.successful(Ok)
  }

  def methodWithDisabledCookies() = SecuredAction(authCookieEnabled = Some(false)).async {
    Future.successful(Ok)
  }
}
