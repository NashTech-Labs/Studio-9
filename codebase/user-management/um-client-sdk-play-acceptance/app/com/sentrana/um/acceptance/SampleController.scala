package com.sentrana.um.acceptance

import javax.inject.{ Inject, Singleton }

import com.sentrana.um.client.play.{ JsonFormats, SecuredController, UmClient }
import com.sentrana.umserver.shared.dtos.{ DataFilterInstance, Organization, User }
import play.api.libs.json.Json
import play.api.mvc.Action

import scala.concurrent.Future

/**
 * Created by Paul Lysak on 21.04.16.
 */
@Singleton
class SampleController @Inject() (val umClient: UmClient) extends SecuredController {
  /**
   * Doesn't require any authentication
   *
   * @return
   */
  def publicAction = Action { req =>
    Ok("{}")
  }

  /**
   * Requires to be authenticated as any valid user
   *
   * @return
   */
  def authenticatedAction = SecuredAction { req =>
    Ok("{}")
  }

  /**
   * Returns user assotiated with provided token
   *
   * @return
   */
  def currentUserAction = SecuredAction { req =>
    import JsonFormats._
    implicit val dataFilterInstanceWrites = Json.writes[DataFilterInstance]
    implicit val orgWrites = Json.writes[Organization]
    implicit val userWrites = Json.writes[User]

    Ok(Json.toJson(req.user))
  }

  /**
   * Custom rule - function that decides which user should have access to the action
   *
   * @return
   */
  def customAuthorizationAction = SecuredAction((u, _) => Future.successful(u.firstName == "Bill")) { req =>
    Ok("{}")
  }

  /**
   * Requires user to have SAMPLE_PERMISSION permission
   *
   * @return
   */
  def permissionAuthorizationAction = SecuredAction(RequirePermission("SAMPLE_PERMISSION")) { req =>
    Ok("{}")
  }
}
