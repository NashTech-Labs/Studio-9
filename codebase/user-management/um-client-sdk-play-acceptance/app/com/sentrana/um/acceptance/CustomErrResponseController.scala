package com.sentrana.um.acceptance

import javax.inject.{ Inject, Singleton }

import com.sentrana.um.client.play.{ SecuredController, UmClient }
import com.sentrana.umserver.shared.BaseSecuredController.SecuredRequest
import play.api.Configuration
import play.api.mvc.{ Request, Result }

/**
 * Created by Paul Lysak on 11.07.16.
 */
@Singleton
class CustomErrResponseController @Inject() (val umClient: UmClient) extends SecuredController {
  def permissionAuthorizationAction = SecuredAction(RequirePermission("SAMPLE_PERMISSION")) { req =>
    Ok("welcome!")
  }

  override protected def onForbidden[A](request: SecuredRequest[A]): Result = Ok("please stay out")

  override protected def onUnauthenticated[A](request: Request[A]): Result = Ok("who are you?")
}
