package com.sentrana.umserver.shared

import com.sentrana.umserver.shared.dtos.{ GenericResponse, User }
import com.sentrana.umserver.shared.dtos.enums.WellKnownPermissions
import play.api.Configuration
import play.api.mvc._

import scala.async.Async
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Paul Lysak on 26.04.16.
 */
trait BaseSecuredController extends Controller {
  import BaseSecuredController._

  object SecuredAction extends SecuredActionBuilder((_, _) => Future.successful(true), None) {
    def apply[A]() = this

    def apply[A](authCookieEnabled: Option[Boolean]) = new SecuredActionBuilder((_, _) => Future.successful(true), authCookieEnabled)

    def apply[A](authorization: Authorization, authCookieEnabled: Option[Boolean] = None) = new SecuredActionBuilder(authorization, authCookieEnabled)
  }

  class SecuredActionBuilder(authorization: Authorization, authCookieEnabled: Option[Boolean]) extends ActionBuilder[SecuredRequest] {
    def invokeBlock[A](request: Request[A], block: (SecuredRequest[A]) => Future[Result]) = {
      Async.async {
        val reqOpt = Async.await(secureRequest(request, authCookieEnabled))
        val authorized = Async.await(futureOption(reqOpt.map(sr => authorization(sr.user, sr.request)))).getOrElse(false)
        reqOpt match {
          case Some(sr) if authorized => Async.await(block(sr))
          case Some(sr) => onForbidden(sr)
          case None => onUnauthenticated(request)
        }
      }
    }
  }

  private def secureRequest[A](req: Request[A], authCookieEnabled: Option[Boolean]): Future[Option[SecuredRequest[A]]] = {
    val tokenOpt: Option[String] = req.headers.get("Authorization").flatMap(_.trim.split(" ").toSeq.filter(_.nonEmpty) match {
      case Seq("Bearer", token) => Some(token)
      case Seq("bearer", token) => Some(token)
      case _ => None
    }).orElse(req.getQueryString(ACCESS_TOKEN_QUERY_PARAM)).
      orElse(getTokenFromCookies(req, authCookieEnabled))

    tokenOpt.fold(Future.successful[Option[SecuredRequest[A]]](None))(t =>
      userByToken(t).map(_.map(SecuredRequest(_, t, req))))
  }

  protected def rootOrgId: String

  protected def userByToken(token: String): Future[Option[User]]

  protected def futureOption[A](in: Option[Future[A]]): Future[Option[A]] = {
    in.fold[Future[Option[A]]](Future.successful(None))(_.map(Option.apply))
  }

  protected def onForbidden[A](request: SecuredRequest[A]): Result = {
    GenericResponse("Forbidden").forbidden
  }

  protected def onUnauthenticated[A](request: Request[A]): Result = {
    GenericResponse("Authentication required").unauthorized.discardingCookies(DiscardingCookie(ACCESS_TOKEN_COOKIE))
  }

  private def getTokenFromCookies[A](req: Request[A], authCookieEnabledOpt: Option[Boolean]): Option[String] = {
    val cookieEnabled = authCookieEnabledOpt.getOrElse(authCookieEnabled)
    if (cookieEnabled) req.cookies.get(ACCESS_TOKEN_COOKIE).map(_.value) else None
  }

  protected def authCookieEnabled: Boolean = false

  /**
   *
   * @param permission
   * @param scopeOrgId If Some - check that user either belongs to root org or specified one. If none - skip org check.
   * @return
   */
  def RequirePermission(permission: String, scopeOrgId: Option[String] = Some(rootOrgId)): Authorization =
    {
      case (u, req) =>
        Future.successful {
          val orgMatch = scopeOrgId.filterNot(_ => u.fromRootOrg).forall(_ == u.organizationId)
          u.hasPermission(permission) && orgMatch
        }
    }

  def RequirePermissionEnum(permission: WellKnownPermissions, scopeOrgId: Option[String] = None): Authorization =
    RequirePermission(permission.toString, scopeOrgId)

}

object BaseSecuredController {
  case class SecuredRequest[A](user: User, accessToken: String, request: Request[A]) extends WrappedRequest(request)

  type Authorization = (User, Request[Any]) => Future[Boolean]

  type OrgIdResolver = Request[Any] => Future[Option[String]]

  val ACCESS_TOKEN_QUERY_PARAM = "access_token"
  val ACCESS_TOKEN_COOKIE = "access_token"
}
