package com.sentrana.um.client.play

import com.sentrana.umserver.shared.BaseSecuredController
import com.sentrana.umserver.shared.dtos.User

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

/**
 * Created by Paul Lysak on 21.04.16.
 */
trait SecuredController extends BaseSecuredController {

  protected def umClient: UmClient

  override protected def userByToken(token: String): Future[Option[User]] = {
    umClient.validateAccessToken(token)
  }

  //Called once per application run, so it's ok to await here
  override protected def rootOrgId: String = umClient.rootOrgId
}
