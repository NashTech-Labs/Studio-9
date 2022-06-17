package com.sentrana.um.client.play

import java.net.URLEncoder
import javax.inject.{ Inject, Provider }

import com.ning.http.client.AsyncHttpClientConfigBean
import com.sentrana.um.client.play.exceptions._
import com.sentrana.umserver.shared.dtos._
import com.sentrana.umserver.shared.dtos.enums.UserConverterOptions
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.cache.CacheApi
import play.api.http.{ HttpVerbs, Status, Writeable }
import play.api.libs.json.Json
import play.api.libs.ws.ning.NingWSClient
import play.api.libs.ws.{ WSClient, WSRequest, WSResponse }
import play.api.mvc._

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Created by Paul Lysak on 21.04.16.
 */
class UmClientImpl(
    umServiceUrl: String,
    clientId: String,
    clientSecret: String,
    cache: CacheApi,
    val wsClient: WSClient,
    accessTokenLifetime: Duration = 15.seconds,
    val timeout: Duration = 30.seconds
) extends UmClient with WsRequestResponseConversions {

  import UmClientImpl.log

  private[play] val baseUrl = s"${umServiceUrl.stripSuffix("/")}/api/um-service/v0.1"

  import JsonFormats._

  private implicit val dfiWrites = Json.writes[DataFilterInstance]
  private implicit val updUserAReqWrites = Json.writes[UpdateUserAdminRequest]

  override def forwardToUmServer(request: Request[RawBuffer], path: String): Future[Result] = {
    import Writeable.wBytes
    val url = baseUrl + path

    val content = getRawContent(request.body)
    val wsResponse = request.method match {
      case HttpVerbs.GET => buildWsRequest(request, url).get()
      case HttpVerbs.POST => buildWsRequest(request, url).post(content)
      case HttpVerbs.PUT => buildWsRequest(request, url).put(content)
      case HttpVerbs.DELETE => buildWsRequest(request, url).delete()
      case _ => throw new UmServerException("Not supported http method")
    }

    genericWsResponseToResult(wsResponse)
  }

  override def signIn(username: String, password: String, orgId: Option[String] = None): Future[Option[(String, Int)]] = {
    log.debug(s"Signing in by username $username...")
    val params = Map("username" -> username, "password" -> password, "grant_type" -> "password") ++
      orgId.map(id => "organization_id" -> id).toMap
    sendSignIn(params, username)
  }

  override def signInByEmail(email: String, password: String, orgId: Option[String]): Future[Option[(String, Int)]] = {
    log.debug(s"Signing in by email $email...")
    val params = Map("email" -> email, "password" -> password, "grant_type" -> "password") ++
      orgId.map(id => "organization_id" -> id).toMap
    sendSignIn(params, email)
  }

  private def sendSignIn(params: Map[String, String], userIdentifier: String): Future[Option[(String, Int)]] = {
    async {
      val resp = await(wsClient.url(baseUrl + "/token").
        post(params.map { case (k, v) => k -> Seq(v) }))
      resp.status match {
        case Status.OK =>
          val j = resp.json
          log.debug(s"Successfully signed in $userIdentifier")
          Option(((j \ "access_token").as[String], (j \ "expires_in").as[Int]))
        case Status.BAD_REQUEST =>
          val errorDescription = (resp.json \ "error_description").asOpt[String].getOrElse("")
          log.debug(s"Failed to sign in $userIdentifier. With an error description: ${errorDescription}.")
          None
        case _ =>
          log.error(s"Technical issue when signing in $userIdentifier: ${resp.body}")
          throw new UmServerException(extractMessage(resp))
      }
    }
  }

  override def signOut(token: String): Future[Unit] = {
    log.debug(s"Signing out $token...")
    async {
      await(wsClient.url(baseUrl + s"/token/${token}").delete())
      ()
    }
  }

  override def validateAccessToken(token: String, withOrgDetails: Boolean = false, withTimeZone: Option[String] = None): Future[Option[User]] = {
    log.debug(s"Validating access token $token...")
    async {
      cache.get[User](token) match {
        case Some(user) => Option(user)
        case _ =>
          val encToken = URLEncoder.encode(token, "UTF-8") //to avoid technical issue on invalid tokens
          val resp = await(wsClient.url(baseUrl + s"/token/$encToken/user").withQueryString(userOptions(withOrgDetails, withTimeZone): _*).get())
          resp.status match {
            case Status.OK => addUserToCacheByParameters(Option(resp.json.as[User]), token, withOrgDetails, withTimeZone)
            case Status.BAD_REQUEST => None
            case _ =>
              log.error(s"Technical issue when verifying token  $token: ${resp.body}")
              throw new UmServerException(extractMessage(resp))
          }
      }
    }
  }

  private def addUserToCacheByParameters(userOpt: Option[User], token: String, withOrgDetails: Boolean, withTimeZone: Option[String]): Option[User] = {
    cache.set(accessTokenCacheKey(token, withOrgDetails, withTimeZone), userOpt, accessTokenLifetime)
    userOpt
  }

  override def getRootOrg(): Future[Organization] = {
    log.debug("Retrieving root org...")
    val request = wsClient.url(baseUrl + "/orgs/root")
    import JsonFormats._
    callSecuredMethod(request, get).map { resp =>
      resp.json.as[Organization]
    }
  }

  override def getUser(userId: String, withOrgDetails: Boolean = false, withTimeZone: Option[String] = None): Future[Option[User]] = {
    log.debug(s"Getting userGroup by $userId...")
    val request = wsClient.url(baseUrl + s"/orgs/${rootOrgId}/users/$userId").withQueryString(userOptions(withOrgDetails, withTimeZone): _*)
    import JsonFormats._
    callSecuredMethod(request, get).map { resp =>
      resp.status match {
        case Status.OK => Option(resp.json.as[User])
        case Status.NOT_FOUND => None
        case _ => throw new UmServerException(s"Unable to get user by ${userId}: ${resp.body}")
      }
    }
  }

  override def findUsers(
    username: Option[String] = None,
    email: Option[String] = None,
    emailPrefix: Option[String] = None,
    orgId: Option[String] = None,
    offset: Int = 0,
    limit: Int = 10
  ): Future[Seq[User]] = {
    import JsonFormats._
    log.debug(s"Searching user with email=$email, emailPrefix=$emailPrefix...")
    val q =
      orgId.map("organizationId" -> _).toSeq ++
        username.map("username" -> _).toSeq ++
        email.map("email_case_insensitive" -> _).toSeq ++
        emailPrefix.map("email_prefix" -> _).toSeq ++
        Seq("offset" -> offset.toString, "limit" -> limit.toString)
    val request = wsClient.url(baseUrl + s"/orgs/${rootOrgId}/users").withQueryString(q: _*)
    callSecuredMethod(request, get).map { resp =>
      resp.status match {
        case Status.OK => resp.json.as[SeqPartContainer[User]].data
        case _ => throw new UmServerException(s"Failed to get user: ${resp.body}")
      }
    }
  }

  override def getUserGroup(groupId: String): Future[Option[UserGroup]] = {
    log.debug(s"Getting userGroup by $groupId...")
    val request = wsClient.url(baseUrl + s"/orgs/${rootOrgId}/groups/$groupId")
    import JsonFormats._
    callSecuredMethod(request, get).map { resp =>
      resp.status match {
        case Status.OK => Option(resp.json.as[UserGroup])
        case Status.NOT_FOUND => None
        case _ => throw new UmServerException(s"Unable to get userGroup by ${groupId}: ${resp.body}")
      }
    }
  }

  def getApplicationAccessToken(): Future[Option[String]] = {
    log.debug(s"Getting application access token by $clientId and $clientSecret...")
    async {
      val resp = await(wsClient.url(baseUrl + "/token").
        post(Map("client_id" -> Seq(clientId), "client_secret" -> Seq(clientSecret), "grant_type" -> Seq("client_credentials"))))
      resp.status match {
        case Status.OK =>
          Option((resp.json \ "access_token").as[String]).map { accessToken =>
            applicationAccessToken = Some(accessToken)
            accessToken
          }
        case Status.BAD_REQUEST =>
          log.error(s"Application $clientId and secret $clientSecret is unable to login with the response: ${resp.body}")
          None
        case _ =>
          log.error(s"Technical issue when getting application access_token $clientId and $clientSecret: ${resp.body}")
          throw new UmServerException(extractMessage(resp))
      }
    }
  }

  override def findFilters(fieldName: Option[String], offset: Int, limit: Int): Future[Seq[DataFilterInfo]] = {
    import JsonFormats._

    log.debug(s"Searching data filters with fieldName=$fieldName...")
    val q = fieldName.map("fieldName" -> _).toSeq
    val request = wsClient.url(baseUrl + s"/filters").withQueryString(q: _*)
    callSecuredMethod(request, get).map { resp =>
      resp.status match {
        case Status.OK =>
          resp.json.as[SeqPartContainer[DataFilterInfo]].data
        case _ => throw new UmServerException(s"Failed to find data filters: ${resp.body}")
      }
    }
  }

  override def getFilterInstances(userId: String): Future[Map[String, DataFilterInstance]] = {
    log.debug(s"Getting pre-processed data filter instances for user $userId ...")
    val request = wsClient.url(baseUrl + s"/orgs/$rootOrgId/users/$userId/filterinstances")
    import JsonFormats._
    callSecuredMethod(request, get).map { resp =>
      resp.status match {
        case Status.OK =>
          resp.json.as[Map[String, DataFilterInstance]]
        case _ =>
          throw new UmServerException(s"Failed to get pre-processed filter instances for user $userId: ${resp.status} ${resp.body}")
      }
    }
  }

  /**
   * Create or replace filter instance for specific user and filter id = filterInstance.dataFilterId
   *
   * @param user
   * @param filterInstance
   * @param accessToken access token of user which performs operation
   * @return
   */
  override def setFilterInstance(user: User, filterInstance: DataFilterInstance, accessToken: String): Future[User] = {
    val fis = user.dataFilterInstances.filterNot(_.dataFilterId == filterInstance.dataFilterId) + filterInstance
    wsClient.url(baseUrl + s"/orgs/${user.organizationId}/users/${user.id}").
      withHeaders("Content-Type" -> "application/json", authHeader(accessToken)).
      put(Json.toJson(UpdateUserAdminRequest(dataFilterInstances = Option(fis)))).map { resp =>
        resp.status match {
          case Status.OK =>
            log.info(s"Successfully set filter instance $filterInstance for user ${user.id}")
            user.copy(dataFilterInstances = fis)
          case Status.BAD_REQUEST =>
            throw new UmValidationException(s"Could not set filter instance $filterInstance for user ${user.id}: ${resp.body}")
          case Status.UNAUTHORIZED =>
            throw new UmAuthenticationException(s"Authentication required. Probably, provided access token was invalid: ${resp.body}")
          case Status.FORBIDDEN =>
            throw new UmAccessDeniedException(s"Forbidden for current user: ${resp.body}")
          case _ =>
            throw new UmServerException(s"Could not set filter instance $filterInstance for user ${user.id}: ${resp.status} ${resp.body}")
        }
      }
  }

  override def signUp(
    orgId: String,
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    sendActivationEmail: Boolean = true
  ): Future[String] = {
    import JsonFormats.userSignUpRequestWrites
    async {
      val resp = await(wsClient.url(baseUrl + s"/orgs/${orgId}/users/signup").
        withHeaders("Content-Type" -> "application/json").
        post(Json.toJson(UserSignUpRequest(username, email, password, firstName, lastName, Option(sendActivationEmail)))))

      resp.status match {
        case Status.OK =>
          (resp.json \ "id").as[String]
        case Status.BAD_REQUEST =>
          log.error(s"Unable to sign-up user ${username}: ${resp.body}")
          throw new UmValidationException(extractMessage(resp))
        case _ =>
          log.error(s"Issue during user ${username} sign-up: ${resp.body}")
          throw new UmServerException(extractMessage(resp))
      }
    }
  }

  override def reSendActivationLink(email: String): Future[Unit] = {
    import JsonFormats.reSendActivationLinkRequestWrites
    async {
      val resp = await(wsClient.url(baseUrl + s"/anonymous/email/activation").
        withHeaders("Content-Type" -> "application/json").
        post(Json.toJson(ReSendActivationLinkRequest(email))))

      resp.status match {
        case Status.OK =>
          ()
        case Status.BAD_REQUEST =>
          log.error(s"Unable to re-send activation link to email ${email}: ${resp.body}")
          throw new UmValidationException(extractMessage(resp))
        case Status.NOT_FOUND =>
          log.error(s"Item not found during re-sending activation link to email ${email}: ${resp.body}")
          throw new UmValidationException(extractMessage(resp))
        case _ =>
          log.error(s"Technical issue during re-sending activation link to email ${email}: ${resp.body}")
          throw new UmServerException(extractMessage(resp))
      }
    }
  }

  /**
   * Initiate password reset. Doesn't really removes the password, but generates secret code that can be used for
   * password rest and sends it to user's email.
   *
   * @param email
   * @return
   */
  override def initPasswordReset(email: String): Future[Unit] = {
    import JsonFormats.passwordResetRequestWrites
    async {
      val resp = await(wsClient.url(baseUrl + s"/anonymous/password/reset").
        withHeaders("Content-Type" -> "application/json").
        post(Json.toJson(PasswordResetRequest(email, appId = Option(clientId)))))

      resp.status match {
        case Status.OK =>
          ()
        case _ =>
          log.error(s"Technical issue during password reset init for email ${email}, client ${clientId}: ${resp.body}")
          throw new UmServerException(extractMessage(resp))
      }
    }
  }

  override def completePasswordReset(email: String, secretCode: String, newPassword: String): Future[Unit] = {
    import JsonFormats.updateForgottenPasswordRequestWrites
    async {
      val resp = await(wsClient.url(baseUrl + s"/anonymous/password").
        withHeaders("Content-Type" -> "application/json").
        post(Json.toJson(UpdateForgottenPasswordRequest(
          email = email,
          secretCode = secretCode,
          newPassword = newPassword
        ))))

      resp.status match {
        case Status.OK =>
          ()
        case Status.BAD_REQUEST =>
          log.error(s"Unable to update forgotten password for email ${email}: ${resp.body}")
          throw new UmValidationException(extractMessage(resp))
        case Status.UNAUTHORIZED =>
          log.error(s"Authentication failed during forgotten password update for email ${email}: ${resp.body}")
          throw new UmAuthenticationException(extractMessage(resp))
        case Status.NOT_FOUND =>
          log.error(s"Item not found during forgotten password update for email ${email}: ${resp.body}")
          throw new UmValidationException(extractMessage(resp))
        case _ =>
          log.error(s"Technical issue during forgotten password update for email ${email}: ${resp.body}")
          throw new UmServerException(extractMessage(resp))
      }
    }
  }

  override def updatePassword(accessToken: String, oldPassword: String, newPassword: String): Future[Unit] = {
    import JsonFormats.updatePasswordRequestWrites
    async {
      val resp = await(wsClient.url(baseUrl + s"/me/password").
        withHeaders("Content-Type" -> "application/json", authHeader(accessToken)).
        post(Json.toJson(UpdatePasswordRequest(
          oldPassword = oldPassword,
          newPassword = newPassword
        ))))

      resp.status match {
        case Status.OK =>
          ()
        case Status.BAD_REQUEST =>
          log.error(s"Unable to update password with token ${accessToken}: ${resp.body}")
          throw new UmValidationException(extractMessage(resp))
        case Status.UNAUTHORIZED =>
          log.error(s"Authentication failed during password update with token ${accessToken}: ${resp.body}")
          throw new UmAuthenticationException(extractMessage(resp))
        case _ =>
          log.error(s"Technical issue during password update with token ${accessToken}: ${resp.body}")
          throw new UmServerException(extractMessage(resp))
      }
    }
  }

  private def userOptions(withOrgDetails: Boolean, withTimeZone: Option[String]): Seq[(String, String)] = {
    val wOrg = Seq(UserConverterOptions.withOrgDetails.toString -> "true").filter(_ => withOrgDetails)
    val wTz = withTimeZone.map(tz => UserConverterOptions.withTimeZone.toString -> tz).toSeq

    wOrg ++ wTz
  }

  private def get(request: WSRequest): Future[WSResponse] = {
    request.get()
  }

  private def authHeader(accessToken: String): (String, String) = "Authorization" -> s"Bearer $accessToken"

  private def callSecuredMethod(request: WSRequest, wsCall: WSRequest => Future[WSResponse], useTokenFromCache: Boolean = true): Future[WSResponse] = {
    async {
      await(getAccessToken(useTokenFromCache)) match {
        case Some(token) =>
          val resp = await(wsCall(request.withHeaders(authHeader(token))))
          resp.status match {
            case status if (isValidStatus(status)) =>
              resp
            case Status.UNAUTHORIZED if (useTokenFromCache) =>
              await(callSecuredMethod(request, wsCall, false))
            case Status.UNAUTHORIZED =>
              log.error(s"Client is not authenticated: ${resp.body}")
              throw new UmAuthenticationException(s"Client is not authenticated: ${resp.body}")
            case Status.FORBIDDEN =>
              log.error(s"Client doesn't have permissions: ${resp.body}")
              throw new UmAccessDeniedException(s"Client doesn't have permissions: ${resp.body}")
            case _ =>
              log.error(s"Internal issue when calling method: ${resp.body}")
              throw new UmServerException(extractMessage(resp))
          }
        case None => throw new UmServerException(s"Unable to get access_token for $clientId")
      }
    }
  }

  private def getAccessToken(useTokenFromCache: Boolean): Future[Option[String]] = {
    async {
      if (applicationAccessToken.isEmpty || !useTokenFromCache) await(getApplicationAccessToken()) else applicationAccessToken
    }
  }

  private def isValidStatus(status: Int): Boolean = !INVALID_STATUSES.contains(status)

  private def accessTokenCacheKey(token: String, withOrgDetails: Boolean, withTimeZone: Option[String]) = s"access.token.${token}.${withOrgDetails}.${withTimeZone}"

  private def extractMessage(resp: WSResponse): String = {
    try {
      (resp.json \ "message").as[String]
    } catch {
      case e: Exception =>
        log.warn(s"Unable to extract message from response, returning body as-is: " + resp.body)
        resp.body
    }
  }

  private var applicationAccessToken: Option[String] = None

  private val INVALID_STATUSES = Set(
    Status.UNAUTHORIZED,
    Status.FORBIDDEN,
    Status.INTERNAL_SERVER_ERROR,
    Status.NOT_IMPLEMENTED,
    Status.BAD_GATEWAY,
    Status.SERVICE_UNAVAILABLE,
    Status.GATEWAY_TIMEOUT,
    Status.HTTP_VERSION_NOT_SUPPORTED,
    Status.INSUFFICIENT_STORAGE
  )

}

object UmClientImpl {
  private val log = LoggerFactory.getLogger(classOf[UmClientImpl])
}

class UmClientImplProvider @Inject() (cfg: Configuration, cache: CacheApi) extends Provider[UmClientImpl] {
  override def get(): UmClientImpl = {
    val serverUrl = getProperty("url")
    val clientId = getProperty("client.id")
    val clientSecret = getProperty("client.secret")
    val timeout = getDuration("timeout", 30.seconds)
    val accessTokenLifetime = getDuration("access.token.lifetime", 15.seconds)

    val config = new AsyncHttpClientConfigBean()
    //can't simply inject default WSClient because of Play bug https://github.com/playframework/playframework/issues/5105
    serverCfg.getBoolean("acceptAnyCertificate").foreach(config.setAcceptAnyCertificate)
    config.setFollowRedirect(true)
    val wsClient = NingWSClient(config)

    new UmClientImpl(serverUrl, clientId, clientSecret, cache, wsClient, accessTokenLifetime, timeout)
  }

  private def getDuration(propertyName: String, defaultValue: Duration): Duration = {
    serverCfg.getMilliseconds(propertyName).map(_.milliseconds).getOrElse({
      log.debug(s"$propertyName not configured, using default $defaultValue")
      defaultValue
    })
  }

  private val serverCfgPrefix = "sentrana.um.server"
  private lazy val serverCfg = cfg.getConfig(serverCfgPrefix).getOrElse(Configuration.empty)

  private def getProperty(propertyName: String): String = serverCfg.getString(propertyName).getOrElse(throw new UmConfigurationException(s"$serverCfgPrefix.$propertyName is not specified"))

  private val log = LoggerFactory.getLogger(classOf[UmClientImplProvider])
}
