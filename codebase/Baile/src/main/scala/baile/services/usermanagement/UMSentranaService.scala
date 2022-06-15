package baile.services.usermanagement

import java.net.URLEncoder
import java.util.UUID

import akka.actor.{ Actor, Scheduler }
import akka.event.LoggingAdapter
import akka.http.caching.scaladsl.Cache
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.{ FromEntityUnmarshaller, Unmarshal }
import akka.pattern.pipe
import akka.stream.Materializer
import baile.domain.usermanagement.Role
import baile.services.http.HttpClientService
import baile.services.usermanagement.datacontract._
import baile.services.usermanagement.datacontract.password._
import baile.services.usermanagement.exceptions.UnexpectedResponseException
import cats.data.EitherT
import cats.implicits._
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.{ ExecutionContext, Future }

object UMSentranaService {

  case class SignIn(userParams: String, password: String, isEmail: Boolean = false)

  case class SignOut(token: String)

  case class SignUp(
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    requireEmailConfirmation: Boolean
  )

  case class ConfirmUser(
    orgId: String,
    userId: UUID,
    activationCode: UUID
  )

  case class InitiatePasswordReset(email: String)

  case class CompletePasswordReset(email: String, secretCode: String, newPassword: String)

  case class GetUser(id: UUID)

  case class DeleteUser(userId: UUID)

  case class FindUsers(
    firstName: Option[String],
    lastName: Option[String],
    username: Option[String] = None,
    email: Option[String] = None,
    emailPrefix: Option[String] = None,
    orgId: Option[String] = None,
    offset: Int = 0,
    limit: Int = 10,
    order: Option[String] = None
  )

  case class UpdatePassword(accessToken: String, oldPassword: String, newPassword: String)

  case class ValidateAccessToken(token: String)

  case class CreateUser(
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    role: Role,
    requireEmailConfirmation: Boolean
  )

  case class UpdateUser(
    userId: UUID,
    username: Option[String],
    email: Option[String],
    password: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    role: Option[Role]
  )

  case class ActivateUser(
    userId: UUID
  )

  case class DeactivateUser(
    userId: UUID,
    sendNotificationEmail: Boolean
  )

}

class UMSentranaService(
  val conf: Config,
  val http: HttpExt,
  val userByTokenCache: Cache[String, UserResponse]
)(implicit val materializer: Materializer,
  val ec: ExecutionContext,
  val logger: LoggingAdapter,
  val scheduler: Scheduler
) extends Actor with HttpClientService with PlayJsonSupport {

  import UMSentranaService._

  type Result[T] = Future[Either[ErrorResponse, T]]

  private val umServiceConfig = conf.getConfig("um-service")
  private val baseUrl = umServiceConfig.getString("url")
  private val appOrgId = umServiceConfig.getString("org-id")
  private val clientId = umServiceConfig.getString("client-id")
  private val clientSecret = umServiceConfig.getString("client-secret")
  private val adminRoleId = umServiceConfig.getString("admin-role-id")

  private var applicationAccessToken: Option[String] = None
  private var rootOrgId: Option[String] = None

  override def receive: Receive = {
    case SignOut(token) =>
      signOut(token) pipeTo sender
    case SignIn(userParams, password, isEmail) =>
      signIn(userParams, password, isEmail) pipeTo sender
    case SignUp(username, email, password, firstName, lastName, requireEmailConfirmation) =>
      signUp(username, email, password, firstName, lastName, requireEmailConfirmation) pipeTo sender
    case ConfirmUser(orgId, userId, activationCode) =>
      confirmUser(orgId, userId, activationCode) pipeTo sender
    case InitiatePasswordReset(email) =>
      initiatePasswordReset(email) pipeTo sender
    case CompletePasswordReset(email, secretCode, newPassword) =>
      completePasswordReset(email, secretCode, newPassword) pipeTo sender
    case GetUser(userId) =>
      getUser(userId) pipeTo sender
    case FindUsers(firstName, lastName, username, email, emailPrefix, orgId, offset, limit, order) =>
      findUsers(firstName, lastName, username, email, emailPrefix, orgId, offset, limit, order) pipeTo sender
    case DeleteUser(userId) =>
      deleteUser(userId) pipeTo sender
    case ValidateAccessToken(token) =>
      validateAccessToken(token) pipeTo sender
    case UpdatePassword(accessToken, oldPassword, newPassword) =>
      updatePassword(accessToken, oldPassword, newPassword) pipeTo sender
    case CreateUser(username, email, password, firstName, lastName, role, requireEmailConfirmation) =>
      createUser(username, email, password, firstName, lastName, role, requireEmailConfirmation) pipeTo sender
    case UpdateUser(userId, username, email, password, firstName, lastName, role) =>
      updateUser(
        userId,
        username,
        email,
        password,
        firstName,
        lastName,
        role
      ) pipeTo sender
    case ActivateUser(userId) => activateUser(userId) pipeTo sender
    case DeactivateUser(userId, sendNotificationEmail) => deactivateUser(userId, sendNotificationEmail) pipeTo sender
  }

  private def signOut(token: String): Future[Unit] =
    makeHttpRequest(
      HttpRequest(method = HttpMethods.DELETE, uri = s"$baseUrl/token/$token"),
      Seq(StatusCodes.OK, StatusCodes.NotFound)
    ).map(_ => ())

  private def signIn(userParams: String, password: String, isEmail: Boolean): Result[TokenResponse] = {
    val loginParam = if (isEmail) "email" -> userParams else "username" -> userParams
    val params = Map("password" -> password, "grant_type" -> "password") + loginParam
    val httpEntity = FormData(params).toEntity
    val request = HttpRequest(method = HttpMethods.POST, entity = httpEntity, uri = s"$baseUrl/token")
    for {
      response <- makeHttpRequest(request, expectedCodes = Seq(StatusCodes.OK, StatusCodes.BadRequest))
      result <- handleResponse[TokenResponse](response)
    } yield result
  }

  private def confirmUser(
    orgId: String,
    userId: UUID,
    activationCode: UUID
  ): Result[ConfirmUserResponse] = {
    val params = Map("activationCode" -> activationCode.toString)
    val uri = s"$baseUrl/orgs/$orgId/users/$userId/activate"
    for {
      response <- makeSecuredHttpRequest(
        HttpRequest(
          HttpMethods.GET,
          Uri(uri).withQuery(Query(params))
        ),
        Seq(StatusCodes.OK, StatusCodes.BadRequest)
      )
      result <- handleResponse[ConfirmUserResponse](response)
    } yield result
  }

  private def signUp(
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    requireEmailConfirmation: Boolean
  ): Result[SignUpResponse] =
    for {
      requestEntity <- Marshal(SignUpRequest(
        username,
        email,
        password,
        firstName,
        lastName,
        requireEmailConfirmation
      )).to[RequestEntity]
      response <- makeSecuredHttpRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$baseUrl/orgs/$appOrgId/users/signup",
          entity = requestEntity
        ),
        Seq(StatusCodes.OK, StatusCodes.BadRequest)
      )
      result <- handleResponse[SignUpResponse](response)
    } yield result

  def initiatePasswordReset(email: String): Future[Unit] =
    for {
      requestEntity <- Marshal(PasswordResetRequest(email = email, appId = Some(clientId))).to[RequestEntity]
      _ <- makeHttpRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$baseUrl/anonymous/password/reset",
          entity = requestEntity
        )
      )
    } yield ()

  private def completePasswordReset(email: String, secretCode: String, newPassword: String): Result[Unit] = {
    val expectedStatuses = Seq(StatusCodes.OK, StatusCodes.BadRequest, StatusCodes.Unauthorized, StatusCodes.NotFound)
    for {
      httpEntity <- Marshal(UpdateForgottenPasswordRequest(
        email,
        secretCode,
        newPassword
      )).to[RequestEntity]
      response <- makeHttpRequest(
        HttpRequest(method = HttpMethods.POST, uri = s"$baseUrl/anonymous/password", entity = httpEntity),
        expectedStatuses
      )
      result <- handleResponse(response)
    } yield result.map(_ => ())
  }

  private def updatePassword(accessToken: String, oldPassword: String, newPassword: String): Result[Unit] =
    for {
      httpEntity <- Marshal(UpdatePasswordRequest(oldPassword, newPassword)).to[RequestEntity]
      request = HttpRequest(method = HttpMethods.POST, uri = s"$baseUrl/me/password", entity = httpEntity)
      response <- makeHttpRequest(
        signRequest(request, accessToken),
        expectedCodes = Seq(StatusCodes.OK, StatusCodes.BadRequest, StatusCodes.Unauthorized)
      )
      result <- handleResponse(response)
    } yield result.map(_ => ())

  private def getUser(userId: UUID): Result[UserResponse] = {
    withRootOrg { rootOrgId =>
      for {
        response <- makeSecuredHttpRequest(
          HttpRequest(
            HttpMethods.GET,
            s"$baseUrl/orgs/$rootOrgId/users/$userId"
          ),
          expectedCodes = Seq(StatusCodes.OK, StatusCodes.NotFound)
        )
        result <- handleResponse[UserResponse](response)
      } yield result
    }
  }

  private def deleteUser(userId: UUID): Result[Unit] = {
    withRootOrg { rootOrgId =>
      for {
        response <- makeSecuredHttpRequest(
          HttpRequest(
            HttpMethods.DELETE,
            s"$baseUrl/orgs/$rootOrgId/users/$userId"
          ),
          expectedCodes = Seq(StatusCodes.OK, StatusCodes.NotFound)
        )
        result <- handleResponse[GenericResponse](response)
      } yield result.map(_ => ())
    }
  }

  private def activateUser(
    userId: UUID
  ): Result[UserResponse] = {
    withRootOrg { rootOrgId =>
      for {
        response <- makeSecuredHttpRequest(
          HttpRequest(
            HttpMethods.POST,
            s"$baseUrl/orgs/$rootOrgId/users/$userId/activateuser"
          ),
          expectedCodes = Seq(StatusCodes.OK, StatusCodes.BadRequest)
        )
        result <- handleResponse[UserResponse](response)
      } yield result
    }
  }

  private def deactivateUser(
    userId: UUID,
    sendNotificationEmail: Boolean
  ): Result[UserResponse] = {
    withRootOrg { rootOrgId =>
      for {
        requestEntity <- Marshal(UserDeactivationRequest(
          sendNotificationEmail = sendNotificationEmail
        )).to[RequestEntity]
        response <- makeSecuredHttpRequest(
          HttpRequest(
            HttpMethods.POST,
            s"$baseUrl/orgs/$rootOrgId/users/$userId/deactivateuser",
            entity = requestEntity
          ),
          Seq(StatusCodes.OK, StatusCodes.BadRequest)
        )
        result <- handleResponse[UserResponse](response)
      } yield result
    }
  }

  private def findUsers(
    firstName: Option[String],
    lastName: Option[String],
    username: Option[String],
    email: Option[String],
    emailPrefix: Option[String],
    orgId: Option[String],
    offset: Int,
    limit: Int,
    order: Option[String]
  ): Result[ListResponse[UserResponse]] =
    withRootOrg { rootOrgId =>
      val params = Map("offset" -> offset.toString, "limit" -> limit.toString) ++
        orgId.map("organizationId" -> _) ++
        username.map("username" -> _) ++
        email.map("email_case_insensitive" -> _) ++
        emailPrefix.map("email_prefix" -> _) ++
        firstName.map("firstName_contains" -> _) ++
        lastName.map("lastName_contains" -> _) ++
        order.map("order" -> _)
      for {
        response <- makeSecuredHttpRequest(HttpRequest(
          HttpMethods.GET,
          Uri(s"$baseUrl/orgs/$rootOrgId/users").withQuery(Query(params))
        ))
        result <- handleResponse[ListResponse[UserResponse]](response)
      } yield result
    }


  private def createUser(
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    role: Role,
    requireEmailConfirmation: Boolean
  ): Result[UserResponse] =
    withRootOrg { rootOrgId =>
      for {
        requestEntity <- Marshal(CreateUserRequest(
          username = username,
          email = email,
          password = password,
          firstName = firstName,
          lastName = lastName,
          groupIds = getRoleId(role).fold(Set.empty[String])(Set(_)),
          requireEmailConfirmation = requireEmailConfirmation
        )).to[RequestEntity]
        response <- makeSecuredHttpRequest(
          HttpRequest(
            method = HttpMethods.POST,
            uri = s"$baseUrl/orgs/$rootOrgId/users",
            entity = requestEntity
          ),
          Seq(StatusCodes.OK, StatusCodes.BadRequest)
        )
        result <- handleResponse[UserResponse](response)
      } yield result
    }

  private def updateUser(
    userId: UUID,
    username: Option[String],
    email: Option[String],
    password: Option[String],
    firstName: Option[String],
    lastName: Option[String],
    role: Option[Role]
  ): Result[UserResponse] =
    withRootOrg { rootOrgId =>
      for {
        requestEntity <- Marshal(UpdateUserRequest(
          username = username,
          email = email,
          password = password,
          firstName = firstName,
          lastName = lastName,
          groupIds = role.map(getRoleId(_).fold(Set.empty[String])(Set(_)))
        )).to[RequestEntity]
        response <- makeSecuredHttpRequest(
          HttpRequest(
            method = HttpMethods.PUT,
            uri = s"$baseUrl/orgs/$rootOrgId/users/$userId",
            entity = requestEntity
          ),
          expectedCodes = Seq(StatusCodes.OK, StatusCodes.BadRequest)
        )
        result <- handleResponse[UserResponse](response)
      } yield result
    }

  private def validateAccessToken(token: String): Result[UserResponse] = {
    userByTokenCache.get(token) match {
      case Some(user) =>
        user.map(_.asRight)
      case None =>
        val encodedToken = URLEncoder.encode(token, "UTF-8")
        val result = for {
          response <- EitherT.right[ErrorResponse](makeHttpRequest(
            HttpRequest(
              method = HttpMethods.GET,
              uri = s"$baseUrl/token/$encodedToken/user"
            ),
            Seq(StatusCodes.OK, StatusCodes.BadRequest)
          ))
          user <- EitherT(handleResponse[UserResponse](response))
          _ <- EitherT.right[ErrorResponse](userByTokenCache.getOrLoad(token, _ => Future.successful(user)))
        } yield user
        result.value
    }
  }

  private def handleResponse[T: FromEntityUnmarshaller](response: HttpResponse): Result[T] =
    response.status match {
      case StatusCodes.OK => Unmarshal(response).to[T].map(_.asRight)
      case _ => Unmarshal(response.entity).to[ErrorResponse].map(_.asLeft)
    }

  private def getApplicationAccessToken(useTokenFromCache: Boolean): Future[String] =
    applicationAccessToken match {
      case Some(token) if useTokenFromCache =>
        Future.successful(token)
      case _ =>
        val requestEntity = FormData(
          "client_id" -> clientId,
          "client_secret" -> clientSecret,
          "grant_type" -> "client_credentials"
        ).toEntity

        for {
          response <- makeHttpRequest(HttpRequest(
            method = HttpMethods.POST,
            uri = s"$baseUrl/token",
            entity = requestEntity
          ))
          result <- handleResponse[TokenResponse](response)
        } yield result match {
          case Right(TokenResponse(token, _, _)) =>
            applicationAccessToken = Some(token)
            token
          case Left(errorResponse) =>
            throw UnexpectedResponseException(
              s"Could not get application access token. Got error response: $errorResponse"
            )
        }
    }

  private def makeSecuredHttpRequest(
    request: HttpRequest,
    expectedCodes: Seq[StatusCode] = Seq(StatusCodes.OK),
    refreshToken: Boolean = false
  ): Future[HttpResponse] = {

    def makeRequest(applicationToken: String): Future[HttpResponse] = {
      val additionalCodes = Seq(
        StatusCodes.Unauthorized,
        StatusCodes.Forbidden
      )
      makeHttpRequest(signRequest(request, applicationToken), expectedCodes ++ additionalCodes)
    }

    for {
      applicationToken <- getApplicationAccessToken(!refreshToken)
      response <- makeRequest(applicationToken)
      result <- response.status match {
        case StatusCodes.Unauthorized | StatusCodes.Forbidden =>
          if (refreshToken) throw UnexpectedResponseException("Did not pass authorization with fresh token")
          else makeSecuredHttpRequest(request, expectedCodes, refreshToken = true)
        case _ => Future.successful(response)
      }
    } yield result
  }

  private def signRequest(request: HttpRequest, token: String): HttpRequest =
    request.withHeaders(RawHeader("Authorization", s"Bearer $token"))

  private def withRootOrg[R](f: String => Future[R]): Future[R] =
    rootOrgId match {
      case Some(id) => f(id)
      case None =>
        val organizationF = for {
          response <- makeSecuredHttpRequest(HttpRequest(HttpMethods.GET, s"$baseUrl/orgs/root"))
          organization <- Unmarshal(response.entity).to[OrganizationResponse]
        } yield {
          rootOrgId = Some(organization.id)
          organization
        }

        for {
          organization <- organizationF
          result <- f(organization.id)
        } yield {
          result
        }
    }

  private def getRoleId(role: Role): Option[String] = role match {
    case Role.Admin => Some(adminRoleId)
    case Role.User => None
  }

}
