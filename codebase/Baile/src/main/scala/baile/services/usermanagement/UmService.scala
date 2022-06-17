package baile.services.usermanagement

import java.util.UUID

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.util.Timeout
import baile.domain.usermanagement._
import baile.services.usermanagement.UmService.SignInError.InvalidCredentials
import baile.services.usermanagement.UmService.SignUpError.{ EmailAlreadyTaken, UsernameAlreadyTaken }
import baile.services.usermanagement.UmService._
import baile.services.usermanagement.datacontract._
import baile.services.usermanagement.exceptions._
import baile.utils.MailService
import baile.utils.TryExtensions._
import cats.data.EitherT
import cats.implicits._
import com.typesafe.config.Config

import scala.concurrent.duration.SECONDS
import scala.concurrent.{ ExecutionContext, Future }
import scala.io.Source

class UmService(
  ownershipTransferRegistry: OwnershipTransferRegistry,
  config: Config,
  umClient: ActorRef,
  mailService: MailService,
  logger: LoggingAdapter
)(
  implicit val ec: ExecutionContext
) {

  import UMSentranaService._

  private val umServiceConfig: Config = config.getConfig("um-service")
  private val usernameRemindTemplatePath: String = umServiceConfig.getString("username-remind-template")
  private val timeoutDuration: Timeout = Timeout(umServiceConfig.getLong("um-timeout"), SECONDS)
  private val adminRoleId = umServiceConfig.getString("admin-role-id")
  private val requireEmailConfirmation = umServiceConfig.getBoolean("require-email-confirmation")
  private implicit val timeout: Timeout = timeoutDuration

  type Result[E, T] = Future[Either[E, T]]
  type Response[T] = Either[ErrorResponse, T]

  def signOut(token: String): Future[Unit] =
    (umClient ? SignOut(token)).mapTo[Unit]

  def signIn(userParams: String, password: String, isEmail: Boolean = false): Result[SignInError, AccessToken] =
    (umClient ? SignIn(userParams, password, isEmail))
      .mapTo[Response[TokenResponse]]
      .map(_.bimap(
        errorResponse => {
          if (errorResponse.error == "invalid_grant") {
            InvalidCredentials
          } else {
            throw UnexpectedResponseException(errorResponse.toString)
          }
        },
        tokenResponse => AccessToken(tokenResponse.access_token, tokenResponse.expires_in, tokenResponse.token_type)
      ))

  def confirmUser(
    orgId: String,
    userId: UUID,
    activationCode: UUID
  ): Future[Either[EmailConfirmationError, Unit]] = {
    (umClient ? ConfirmUser(orgId, userId, activationCode))
      .mapTo[Response[ConfirmUserResponse]]
      .map(_.bimap(
        errorResponse => errorResponse.error match {
          case message if message.contains("The email confirmation link you followed is invalid") =>
            EmailConfirmationError.InvalidEmailConfirmationLink
        },
        _ => ()
      ))
  }

  def signUp(
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String
  ): Result[SignUpError, RegularUser] = {

    def registerUser(): Future[Either[SignUpError, SignUpResponse]] =
      (umClient ? SignUp(username, email, password, firstName, lastName, requireEmailConfirmation))
        .mapTo[Response[SignUpResponse]]
        .map(_.leftMap(errorResponse => errorResponse.error match {
          case message if message.contains(s"User with email $email already registered") =>
            EmailAlreadyTaken(email)
          case message if message.contains(s"User $username already exists") =>
            UsernameAlreadyTaken(username)
          case _ =>
            throw UnexpectedResponseException(errorResponse.toString)
        }))

    def getRegisteredUser(id: UUID): Future[RegularUser] =
      EitherT(getUser(id)).valueOr(error => throw GetRegisteredUserFailedException(error))

    val result = for {
      signUpResponse <- EitherT[Future, SignUpError, SignUpResponse](registerUser())
      user <- EitherT.right[SignUpError].apply[Future, RegularUser](getRegisteredUser(signUpResponse.id))
    } yield user

    result.value
  }

  def initiatePasswordReset(email: String): Future[Unit] =
    (umClient ? InitiatePasswordReset(email)).mapTo[Unit]

  def resetPassword(
    email: String,
    secretCode: String,
    newPassword: String
  ): Result[PasswordResetError, Unit] =
    (umClient ? CompletePasswordReset(email, secretCode, newPassword))
      .mapTo[Response[Unit]]
      .map(_.bimap(
        errorResponse =>
          errorResponse.error match {
            case message if message.contains(s"Password reset code $secretCode is not valid for user") =>
              PasswordResetError.InvalidResetCode
            case message if message.contains(s"No user with email $email were found") =>
              PasswordResetError.UserNotFound(email)
            case message =>
              extractPasswordError(message) match {
                case Some(passwordError) => PasswordResetError.PasswordError(passwordError)
                case None => throw UnexpectedResponseException(errorResponse.toString)
              }
          },
        _ => ()
      ))

  def remindUsername(email: String): Future[Unit] =
    for {
      user <- findUsers(email = Some(email)).map(_.headOption)
      _ <- user match {
        case Some(user) =>
          mailService.sendHtmlFormattedEmail(
            subject = "DeepCortex account name reminder",
            messageBody = Source.fromResource(usernameRemindTemplatePath).mkString
              .replace("#USER_FIRST_NAME", user.firstName)
              .replace("#USERNAME", user.username),
            toAddress = user.email,
            receiverName = user.firstName
          ).toFuture
        case None =>
          Future.unit
      }
    } yield ()

  def getUser(userId: UUID): Result[GetUserError, RegularUser] =
    (umClient ? GetUser(userId))
      .mapTo[Response[UserResponse]]
      .map(_.bimap(
        errorResponse =>
          errorResponse.error match {
            case message if message.equalsIgnoreCase("User not found") =>
              GetUserError.UserNotFound
            case _ =>
              throw UnexpectedResponseException(errorResponse.toString)
          },
        user => user.toDomain(getRole(user.userGroupIds))
      ))

  def findUsers(
    username: Option[String] = None,
    email: Option[String] = None,
    emailPrefix: Option[String] = None,
    orgId: Option[String] = None,
    offset: Int = 0,
    limit: Int = 10
  ): Future[Seq[RegularUser]] =
    (umClient ? FindUsers(
      firstName = None,
      lastName = None,
      username = username,
      email = email,
      emailPrefix = emailPrefix,
      orgId = orgId,
      offset = offset,
      limit = limit,
      order = None
    ))
      .mapTo[Response[ListResponse[UserResponse]]]
      .map(_.valueOr(error => throw UnexpectedResponseException(error.toString)).data.map {
        user => user.toDomain(getRole(user.userGroupIds))
      })

  def validateAccessToken(token: String): Result[GetUserError, RegularUser] =
    (umClient ? ValidateAccessToken(token))
      .mapTo[Response[UserResponse]]
      .map(_.bimap(
        errorResponse => errorResponse.error match {
          case "invalid_grant" if errorResponse.error_description.contains("No such token") =>
            GetUserError.UserNotFound
          case _ =>
            throw UnexpectedResponseException(errorResponse.toString)
        },
        user => user.toDomain(getRole(user.userGroupIds))
      ))

  def updatePassword(
    accessToken: String,
    oldPassword: String,
    newPassword: String
  ): Result[UpdatePasswordError, Unit] =
    (umClient ? UpdatePassword(accessToken: String, oldPassword: String, newPassword: String))
      .mapTo[Response[Unit]]
      .map(_.bimap(
        errorResponse => errorResponse.error match {
          case message if message.contains("Old password doesn't match") =>
            UpdatePasswordError.InvalidOldPassword
          case message if message.contains("Forbidden") =>
            UpdatePasswordError.InvalidToken
          case _ => extractPasswordError(errorResponse.error) match {
            case Some(passwordError) => UpdatePasswordError.PasswordError(passwordError)
            case _ => throw UnexpectedResponseException(errorResponse.toString)
          }
        },
        _ => ()
      ))

  def createUser(
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    role: Role
  )(implicit user: User): Result[UmAdminServiceError, RegularUser] = {
    val result = for {
      _ <- ensureCanPerformAction
      regularUser <- EitherT((umClient ? CreateUser(
        username,
        email,
        password,
        firstName,
        lastName,
        role,
        requireEmailConfirmation
      ))
        .mapTo[Response[UserResponse]]
        .map(_.bimap[UmAdminServiceError, RegularUser](
          errorResponse => errorResponse.error match {
            case message if message.contains(s"User email $email is not unique") =>
              UmAdminServiceError.EmailIsNotUnique(email)
            case message if message.contains(s"User name $username is not unique") =>
              UmAdminServiceError.UsernameIsNotUnique(username)
            case _ =>
              throw UnexpectedResponseException(errorResponse.toString)
          },
          user => user.toDomain(getRole(user.userGroupIds))
        ))
      )
    } yield regularUser
    result.value
  }

  def fetchUser(userId: UUID)(implicit user: User): Result[UmAdminServiceError, RegularUser] = {
    val result = for {
      _ <- ensureCanPerformAction
      regularUser <- EitherT((umClient ? GetUser(userId))
        .mapTo[Response[UserResponse]]
        .map(_.bimap[UmAdminServiceError, RegularUser](
          errorResponse =>
            errorResponse.error match {
              case msg if msg.equalsIgnoreCase("User not found") =>
                UmAdminServiceError.UserNotFound
              case _ =>
                throw UnexpectedResponseException(errorResponse.toString)
            },
          user => user.toDomain(getRole(user.userGroupIds))
        ))
      )
    } yield regularUser
    result.value
  }

  def deleteUser(
    userId: UUID,
    transferOwnershipTo: Option[UUID]
  )(implicit user: User): Result[UmAdminServiceError, Unit] = {

    def ensureAdminCannotDeleteThemself: Either[UmAdminServiceError, Unit] = {
      if (userId == user.id) UmAdminServiceError.AdminCannotDeleteThemself.asLeft
      else ().asRight
    }

    def deleteUser(): Future[Either[UmAdminServiceError, Unit]] = {
      (umClient ? DeleteUser(userId))
        .mapTo[Response[Unit]]
        .map(_.leftMap(
          errorResponse =>
            errorResponse.error match {
              case msg if msg.equalsIgnoreCase("User not found") =>
                UmAdminServiceError.UserNotFound
              case _ =>
                throw UnexpectedResponseException(errorResponse.toString)
            }
        ))
    }

    def ensureNoDanglingAssets(assetCount: Int): Either[UmAdminServiceError, Option[UUID]] =
      if (assetCount > 0) {
        transferOwnershipTo match {
          case None =>  UmAdminServiceError.UserHasAssets.asLeft
          case specified => specified.asRight
        }
      } else {
        None.asRight
      }

    val result = for {
      _ <- ensureCanPerformAction
      _ <- EitherT.fromEither[Future](ensureAdminCannotDeleteThemself)
      assetCount <- EitherT.right[UmAdminServiceError] {
        ownershipTransferRegistry.getAllAssetCount(userId)
      }
      transferTo <- EitherT.fromEither[Future](ensureNoDanglingAssets(assetCount))
      _ <- EitherT(deleteUser())
      _ <- EitherT.right[UmAdminServiceError] {
        transferTo match {
          case Some(id) => ownershipTransferRegistry.transferOwnership(userId, id)
          case None => Future.unit
        }
      }
    } yield ()
    result.value
  }

  def updateUser(
    userId: UUID,
    username: Option[String] = None,
    email: Option[String] = None,
    password: Option[String] = None,
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    role: Option[Role] = None
  )(implicit user: User): Future[Either[UmAdminServiceError, RegularUser]] = {

    def ensureAdminCannotUpdateTheirRole: Either[UmAdminServiceError, Unit] = {
      if (userId == user.id && !role.contains(user.role)) UmAdminServiceError.AdminCannotUpdateTheirRole.asLeft
      else ().asRight
    }

    val result = for {
      _ <- ensureCanPerformAction
      _ <- EitherT.fromEither[Future](ensureAdminCannotUpdateTheirRole)
      regularUser <- EitherT((umClient ? UpdateUser(
        userId = userId,
        username = username,
        email = email,
        password = password,
        firstName = firstName,
        lastName = lastName,
        role = role
      ))
        .mapTo[Response[UserResponse]]
        .map(_.bimap[UmAdminServiceError, RegularUser](
          errorResponse =>
            errorResponse.error match {
              case msg if msg.equalsIgnoreCase("User not found") =>
                UmAdminServiceError.UserNotFound
              case message if email.isDefined && message.contains(s"User email ${ email.get } is not unique") =>
                UmAdminServiceError.EmailIsNotUnique(email.get)
              case message if username.isDefined && message.contains(s"User name ${ username.get } is not unique") =>
                UmAdminServiceError.UsernameIsNotUnique(username.get)
              case _ =>
                throw UnexpectedResponseException(errorResponse.toString)
            },
          user => user.toDomain(getRole(user.userGroupIds))
        )))
    } yield regularUser
    result.value
  }

  def fetchUsers(
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    order: Option[String] = None,
    page: Int,
    pageSize: Int
  )(implicit user: User): Future[Either[UmAdminServiceError, (Seq[RegularUser], Long)]] = {
    val result = for {
      _ <- ensureCanPerformAction
      users <- EitherT((umClient ? FindUsers(
        firstName = firstName,
        lastName = lastName,
        username = None,
        email = None,
        emailPrefix = None,
        orgId = None,
        offset = pageSize * (page - 1),
        limit = pageSize,
        order = order
      ))
        .mapTo[Response[ListResponse[UserResponse]]]
        .map(_.bimap[UmAdminServiceError, (Seq[RegularUser], Long)](
          errorResponse =>
            errorResponse.error match {
              case _ => throw UnexpectedResponseException(errorResponse.toString)
            },
          r => (r.data.map(user => user.toDomain(getRole(user.userGroupIds))), r.total)
        )))
    } yield users
    result.value
  }

  def activateUser(
    userId: UUID
  )(implicit user: User): Future[Either[UmAdminServiceError, RegularUser]] = {
    val result = for {
      _ <- ensureCanPerformAction
      regularUser <- EitherT(
        (umClient ? ActivateUser(userId)).mapTo[Response[UserResponse]]
          .map(_.bimap[UmAdminServiceError, RegularUser](
            errorResponse =>
              errorResponse.error match {
                case msg if msg.equalsIgnoreCase("User not found") =>
                  UmAdminServiceError.UserNotFound
                case _ =>
                  throw UnexpectedResponseException(errorResponse.toString)
              },
            user => user.toDomain(getRole(user.userGroupIds))
          ))
      )
    } yield regularUser
    result.value
  }

  def deactivateUser(
    userId: UUID
  )(implicit user: User): Future[Either[UmAdminServiceError, RegularUser]] = {

    def ensureAdminCannotDeactivateTheirRole: Either[UmAdminServiceError, Unit] = {
      if (userId == user.id) UmAdminServiceError.AdminCannotDeactivateThemself.asLeft
      else ().asRight
    }

    val result = for {
      _ <- ensureCanPerformAction
      _ <- EitherT.fromEither[Future](ensureAdminCannotDeactivateTheirRole)
      regularUser <- EitherT(
        (umClient ? DeactivateUser(userId, requireEmailConfirmation)).mapTo[Response[UserResponse]]
          .map(_.bimap[UmAdminServiceError, RegularUser](
            errorResponse =>
              errorResponse.error match {
                case msg if msg.equalsIgnoreCase("User not found") =>
                  UmAdminServiceError.UserNotFound
                case _ =>
                  throw UnexpectedResponseException(errorResponse.toString)
              },
            user => user.toDomain(getRole(user.userGroupIds))
          ))
      )
    } yield regularUser
    result.value
  }

  private[services] def getUserMandatory(userId: UUID): Future[RegularUser] =
    getUser(userId).map(_.getOrElse(throw new RuntimeException(s"Unexpectedly not found user $userId")))

  private def extractPasswordError(errorMessage: String): Option[PasswordError] = errorMessage match {
    case message if message.contains("Empty password for user") =>
      Some(PasswordError.EmptyPassword)
    case message if message.contains("Invalid passwords length for user") =>
      Some(PasswordError.InvalidPasswordLength(message))
    case message if message.contains("Password previously used for user") =>
      Some(PasswordError.PreviouslyUsedPassword)
    case message if message.contains("Password must contain at least 3 out of 4 character types:" +
      " uppercase letter, lowercase letter, special character, digit") =>
      Some(PasswordError.InvalidPasswordFormat(message))
    case _ =>
      None
  }

  private def ensureCanPerformAction(implicit user: User): EitherT[Future, UmAdminServiceError, Unit] =
    EitherT.cond[Future](
      user.permissions.contains(Permission.SuperUser),
      (),
      UmAdminServiceError.AccessDenied
    )

  private def getRole(roleIds: Set[String]): Role =
    if (roleIds.contains(adminRoleId)) Role.Admin else Role.User
}

object UmService { self =>

  sealed trait GetUserError

  object GetUserError {

    case object UserNotFound extends GetUserError

  }

  sealed trait SignInError

  object SignInError {

    case object InvalidCredentials extends SignInError

  }

  sealed trait SignUpError

  object SignUpError {

    case class UsernameAlreadyTaken(username: String) extends SignUpError

    case class EmailAlreadyTaken(email: String) extends SignUpError

  }

  sealed trait PasswordError

  object PasswordError {

    case object EmptyPassword extends PasswordError

    case object PreviouslyUsedPassword extends PasswordError

    case class InvalidPasswordLength(message: String) extends PasswordError

    case class InvalidPasswordFormat(message: String) extends PasswordError

  }

  sealed trait PasswordResetError

  object PasswordResetError {

    case class UserNotFound(email: String) extends PasswordResetError

    case object InvalidResetCode extends PasswordResetError

    case class PasswordError(error: self.PasswordError) extends PasswordResetError

  }

  sealed trait UpdatePasswordError

  object UpdatePasswordError {

    case object InvalidOldPassword extends UpdatePasswordError

    case object InvalidToken extends UpdatePasswordError

    case class PasswordError(error: self.PasswordError) extends UpdatePasswordError

  }

  sealed trait EmailConfirmationError

  object EmailConfirmationError {

    case object InvalidEmailConfirmationLink extends EmailConfirmationError

  }

  sealed trait UmAdminServiceError

  object UmAdminServiceError {

    case object AdminCannotDeactivateThemself extends UmAdminServiceError

    case object AdminCannotDeleteThemself extends UmAdminServiceError

    case object AdminCannotUpdateTheirRole extends UmAdminServiceError

    case object UserHasAssets extends UmAdminServiceError

    case object AccessDenied extends UmAdminServiceError

    case object UserNotFound extends UmAdminServiceError

    case class UsernameIsNotUnique(username: String) extends UmAdminServiceError

    case class EmailIsNotUnique(email: String) extends UmAdminServiceError

  }

}
