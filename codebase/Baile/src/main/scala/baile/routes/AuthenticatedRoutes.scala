package baile.routes

import akka.http.scaladsl.model.headers.{ BasicHttpCredentials, HttpChallenges }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ AuthenticationFailedRejection, Directive1 }
import baile.domain.usermanagement.User
import baile.routes.AuthenticatedRoutes._
import baile.services.common.AuthenticationService

trait AuthenticatedRoutes extends BaseRoutes {

  val authenticationService: AuthenticationService

  protected val tokenQueryParamName = "access_token"

  def authenticatedWithHeader: Directive1[AuthenticationParameters] =
    for {
      credentials <- extractCredentials
      result <- {
        credentials match {
          case Some(c) if c.scheme.equalsIgnoreCase("Bearer") => authenticate(c.token)
          case _ => rejectUnauthenticated(AuthenticationFailedRejection.CredentialsMissing)
        }
      }
    } yield result

  def authenticatedWithQueryParam: Directive1[AuthenticationParameters] =
    for {
      token <- parameter(tokenQueryParamName.?)
      result <- {
        token match {
          case Some(c) => authenticate(c)
          case _ => rejectUnauthenticated(AuthenticationFailedRejection.CredentialsMissing)
        }
      }
    } yield result

  /** Requires authentication with basic authentication schema.
   *
   * User can authenticate with:
   * - username and password (standard basic auth)
   * - authentication token (`username` field must be empty)
   */
  def authenticatedWithBasicCredentials: Directive1[AuthenticationParameters] =
    for {
      credentials <- extractCredentials
      result <- {
        credentials match {
          case Some(BasicHttpCredentials(username, password)) => authenticate(username, password)
          case _ => rejectUnauthenticated(AuthenticationFailedRejection.CredentialsMissing)
        }
      }
    } yield result

  def authenticated: Directive1[AuthenticationParameters] = authenticatedWithHeader

  private def authenticate(token: String): Directive1[AuthenticationParameters] =
    for {
      response <- onSuccess(authenticationService.authenticate(token))
      result <- handleServiceResult(response, token)
    } yield result

  private def authenticate(username: String, password: String): Directive1[AuthenticationParameters] =
    onSuccess(authenticationService.authenticate(username, password)).flatMap(handleServiceResult(_, password))

  private def handleServiceResult(result: Option[User], token: String): Directive1[AuthenticationParameters] =
    result match {
      case Some(user) => provide(AuthenticationParameters(user, token))
      case None => rejectUnauthenticated(AuthenticationFailedRejection.CredentialsRejected)
    }

  private def rejectUnauthenticated(cause: AuthenticationFailedRejection.Cause): Directive1[AuthenticationParameters] =
    reject(AuthenticationFailedRejection(
      cause,
      HttpChallenges.oAuth2("")
    ))

}

object AuthenticatedRoutes {
  case class AuthenticationParameters(
    user: User,
    token: String
  )
}
