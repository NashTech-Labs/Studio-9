package com.sentrana.umserver.controllers

import javax.inject.{ Inject, Singleton }
import com.sentrana.umserver.services._
import com.sentrana.umserver.shared.BaseSecuredController
import com.sentrana.umserver.shared.dtos.{ OAuthErrorResponse, GenericResponse, TokenResponse }
import org.slf4j.LoggerFactory
import play.api.libs.json.Json
import play.api.mvc.{ Action, Controller, Cookie, Result }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

/**
 * Created by Paul Lysak on 13.04.16.
 */
@Singleton
class AuthenticationController @Inject() (
    authenticationService:          AuthenticationService,
    userConverter:                  UserConverter,
    implicit val groupQueryService: UserGroupQueryService,
    implicit val orgQueryService:   OrganizationQueryService
) extends Controller {
  import com.sentrana.umserver.JsonFormats._

  implicit val tokenRespWrites = Json.writes[TokenResponse]

  def issueToken = Action.async(parse.urlFormEncoded) { req =>
    val grantTypeOpt = req.body.get("grant_type").flatMap(_.headOption)
    val setCookie = req.body.get("set_cookie").flatMap(_.headOption).fold(false)(_ == "true")
    grantTypeOpt match {
      case Some("password") =>
        val userNameOpt = req.body.get("username").map(_.headOption).flatten
        val emailOpt = req.body.get("email").map(_.headOption).flatten
        val passwordOpt = req.body.get("password").map(_.headOption).flatten
        val orgIdOpt = req.body.get("organization_id").map(_.headOption).flatten
        passwordAuthentication(userNameOpt, emailOpt, passwordOpt, orgIdOpt, req.remoteAddress, setCookie)
      case Some("client_credentials") =>
        val clientIdOpt = req.body.get("client_id").map(_.headOption).flatten
        val clientSecretOpt = req.body.get("client_secret").map(_.headOption).flatten
        clientCredentialsAuthentication(clientIdOpt, clientSecretOpt)
      case Some(other) =>
        log.debug(s"Rejecting authentication request with grant_type=$other")
        OAuthErrorResponse.unsupportedGrantType(Option(s"Grant type $other with parameters ${req.queryString} not supported")).resultF
      case _ =>
        log.debug(s"Rejecting authentication request without grant_type")
        OAuthErrorResponse.invalidRequest(Option("grant_type not specified")).resultF
    }
  }

  private def passwordAuthentication(
    userNameOpt:   Option[String],
    emailOpt:      Option[String],
    passwordOpt:   Option[String],
    orgIdOpt:      Option[String],
    remoteAddress: String,
    setCookie:     Boolean
  ): Future[Result] = {
    (userNameOpt, emailOpt, passwordOpt) match {
      case (Some(userName), _, Some(password)) =>
        log.debug(s"Authenticating by username $userName and password...")
        authenticationService.userSignIn(UserName(userName), password, orgIdOpt, remoteAddress).map(handleUserSignIn(_, userName, password, setCookie))
      case (None, Some(email), Some(password)) =>
        log.debug(s"Authenticating user by email $email and password...")
        authenticationService.userSignIn(UserEmail(email), password, orgIdOpt, remoteAddress).map(handleUserSignIn(_, email, password, setCookie))
      case (None, None, _) =>
        log.debug(s"Rejecting authentication request that has neither username, nor email")
        OAuthErrorResponse.invalidRequest(Option("Neither username, nor email is specified")).resultF
      case (_, _, None) =>
        log.debug(s"Rejecting authentication request without password")
        OAuthErrorResponse.invalidRequest(Option("Password not specified")).resultF
    }
  }

  private def clientCredentialsAuthentication(clientIdOpt: Option[String], clientSecretOpt: Option[String]): Future[Result] = {
    (clientIdOpt, clientSecretOpt) match {
      case (Some(clientId), Some(clientSecret)) =>
        authenticationService.applicationSignIn(clientId, clientSecret).map(handleApplicationSignIn(_, clientId, clientSecret))
      case _ =>
        log.debug(s"Rejecting authentication request without client_id or client_secret")
        OAuthErrorResponse.invalidRequest(Option("Either client_id or client_secret not specified")).resultF
    }
  }

  def invalidateToken(token: String) = Action.async { req =>
    authenticationService.invalidateToken(token)
    GenericResponse(s"Token ${token} was invalidated").okF
  }

  def userByToken(token: String) = Action.async { req =>
    val tokenNotFoundResp = OAuthErrorResponse.invalidGrant(Option("No such token")).resultF
    if (Option(token).isDefined && token.startsWith(AuthenticationService.ClientTokenPrefix))
      authenticationService.validateApplicationToken(token).flatMap {
        _.fold(tokenNotFoundResp)(user => Future.successful(Ok(Json.toJson(user))))
      }
    else
      authenticationService.validateToken(token).flatMap(
        _.fold(tokenNotFoundResp)(ue => userConverter.toUserDetailDto(ue, req.queryString).map(u => Ok(Json.toJson(u))))
      )
  }

  private def handleUserSignIn(tokenDurationOpt: Option[(String, Duration)], userName: String, password: String, setCookie: Boolean): Result = {
    handleBaseSignIn(
      tokenDurationOpt,
      s"Couldn't authenticate user $userName $password",
      s"Granting token ${tokenDurationOpt.map(_._1)} to user $userName",
      setCookie
    )
  }

  private def handleApplicationSignIn(appTokenDurationOpt: Option[(String, Duration)], clientId: String, clientSecret: String): Result = {
    handleBaseSignIn(
      appTokenDurationOpt,
      s"Couldn't authenticate application $clientId $clientSecret",
      s"Granting token ${appTokenDurationOpt.map(_._1)} to application $clientId",
      false
    )
  }

  private def handleBaseSignIn(tokenDurationOpt: Option[(String, Duration)], failedSignInMessage: String,
                               successSignInMessage: String, setCookie: Boolean): Result = {
    tokenDurationOpt.fold({
      log.debug(failedSignInMessage)
      //According to OAuth 2 specification wrong password => HTTP code 400
      OAuthErrorResponse.invalidGrant(Option("Invalid credentials")).result
    })({
      case (token, lifetime) =>
        log.debug(successSignInMessage)
        val resp = Ok(Json.toJson(TokenResponse(access_token = token, expires_in = lifetime.toSeconds.toInt)))
        if (setCookie)
          resp.withCookies(Cookie(
            name   = BaseSecuredController.ACCESS_TOKEN_COOKIE,
            value  = token, maxAge = Option(lifetime.toSeconds.toInt)
          ))
        else
          resp
    })
  }

  private val log = LoggerFactory.getLogger(classOf[AuthenticationController])
}

