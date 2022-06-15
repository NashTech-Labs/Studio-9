package baile.routes.usermanagement

import java.util.UUID

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import baile.domain.usermanagement.{ AccessToken, User }
import baile.routes.AuthenticatedRoutes
import baile.routes.contract.common.ErrorResponse._
import baile.routes.contract.common.{ ErrorResponse, IdResponse, ListResponse }
import baile.routes.contract.usermanagement.{ AccessTokenResponse, _ }
import baile.services.common.AuthenticationService
import baile.services.usermanagement.UmService
import baile.services.usermanagement.UmService._
import com.typesafe.config.Config
import play.api.libs.json.JsObject

import scala.concurrent.Future
import scala.util.Either

class UMRoutes(
  val umService: UmService,
  val authenticationService: AuthenticationService,
  val conf: Config
) extends AuthenticatedRoutes {

  val routes: Route =
    path("signin") {
      (post & entity(as[SignInRequest])) {
        case SignInRequest(Some(userName), _, password) =>
          createSignInResponse(umService.signIn(userName, password))
        case SignInRequest(_, Some(email), password) =>
          createSignInResponse(umService.signIn(email, password, isEmail = true))
        case _ =>
          complete(
            errorResponse(StatusCodes.BadRequest, "Neither email no username were provided")
          )
      }
    } ~
    path("signup") {
      (post & entity(as[SignUpRequest])) { signUpData =>
        onSuccess(
          umService.signUp(
            signUpData.username,
            signUpData.email,
            signUpData.password,
            signUpData.firstName,
            signUpData.lastName
          )
        ) {
          case Right(user) => complete(UserResponse.fromDomain(user))
          case Left(error) => complete(translateError(error))
        }
      }
    } ~
    path("emailconfirmation") {
      (post & entity(as[EmailConfirmationRequest])) { emailConfirmationRequest =>
        val data = umService.confirmUser(
          emailConfirmationRequest.orgId,
          emailConfirmationRequest.userId,
          emailConfirmationRequest.activationCode
        )
        onSuccess(data) {
          case Right(_) => complete(StatusCodes.Accepted -> JsObject.empty)
          case Left(error) => complete(translateError(error))
        }
      }
    } ~
    pathPrefix("me") {
      concat(
        pathEnd {
          authenticated { authParams =>
            get {
              onSuccess(umService.getUser(authParams.user.id)) {
                case Right(user) => complete(UserResponse.fromDomain(user))
                case Left(error) => complete(translateError(error))
              }
            }
          }
        },
        pathPrefix("password") {
          concat(
            pathEnd {
              authenticated { authParams =>
                post {
                  entity(as[ChangePasswordRequest]) { changePasswordReq =>
                    onSuccess(umService.updatePassword(
                      authParams.token, changePasswordReq.oldPassword, changePasswordReq.newPassword
                    )) {
                      case Right(_) => complete(StatusCodes.OK -> JsObject.empty)
                      case Left(error) => complete(translateError(error))
                    }
                  }
                }
              }
            },
            path("reset") {
              post {
                entity(as[ResetPasswordRequest]) { request =>
                  umService.initiatePasswordReset(request.email)
                  complete(StatusCodes.Accepted -> JsObject.empty)
                }
              }
            },
            path("resetcomplete") {
              post {
                entity(as[ResetPasswordCompleteRequest]) { req =>
                  onSuccess(umService.resetPassword(req.email, req.secretCode, req.newPassword)) {
                    case Right(_) =>
                      complete(StatusCodes.OK -> JsObject.empty)
                    case Left(error) =>
                      complete(translateError(error))
                  }
                }
              }
            }
          )
        },
        path("username" / "remind") {
          post {
            entity(as[RemindUsernameRequest]) { request =>
              umService.remindUsername(request.email)
              complete(StatusCodes.Accepted -> JsObject.empty)
            }
          }
        }
      )
    } ~
    authenticated { authParams =>
      implicit val user: User = authParams.user
      path("signout") {
        post {
          onSuccess(umService.signOut(authParams.token))(
            complete(StatusCodes.OK -> JsObject.empty)
          )
        }
      } ~
      pathPrefix("users") {
        pathEnd {
          (get & parameters((
            'firstName.as[String].?,
            'lastName.as[String].?,
            'order.as[String].?,
            'page.as[Int].?,
            'page_size.as[Int].?
          ))) { (firstName, lastName, order, page, pageSize) =>
            onSuccess(
              umService.fetchUsers(
                firstName,
                lastName,
                order,
                page.getOrElse(1),
                pageSize.getOrElse(conf.getInt("routes.default-page-size"))
              )
            ) {
              case Right((users, count)) => complete(ListResponse(users.map(UserResponse.fromDomain), count))
              case Left(error) => complete(translateError(error))
            }
          } ~
          (post & entity(as[CreateUserRequest])) { userData =>
            onSuccess(
              umService.createUser(
                userData.username,
                userData.email,
                userData.password,
                userData.firstName,
                userData.lastName,
                userData.role
              )
            ) {
              case Right(user) => complete(UserResponse.fromDomain(user))
              case Left(error) => complete(translateError(error))
            }
          }
        } ~
        pathPrefix(JavaUUID) { id =>
          get {
            onSuccess(umService.fetchUser(id)) {
              case Right(user) => complete(UserResponse.fromDomain(user))
              case Left(error) => complete(translateError(error))
            }
          } ~
          (delete & parameter('transferOwnershipTo.as[UUID].?)) { transferOwnershipTo =>
            onSuccess(umService.deleteUser(id, transferOwnershipTo)) {
              case Right(_) => complete(IdResponse(id.toString))
              case Left(error) => complete(translateError(error))
            }
          } ~
          (put & entity(as[UpdateUserRequest])) { updateRequest =>
            onSuccess(umService.updateUser(
              id,
              updateRequest.username,
              updateRequest.email,
              updateRequest.password,
              updateRequest.firstName,
              updateRequest.lastName,
              updateRequest.role
            )) {
              case Right(user) => complete(UserResponse.fromDomain(user))
              case Left(error) => complete(translateError(error))
            }
          } ~
          path("activate") {
            post {
              onSuccess(umService.activateUser(
                id
              )) {
                case Right(user) => complete(UserResponse.fromDomain(user))
                case Left(error) => complete(translateError(error))
              }
            }
          } ~
          path("deactivate") {
            post {
              onSuccess(umService.deactivateUser(
                id
              )) {
                case Right(user) => complete(UserResponse.fromDomain(user))
                case Left(error) => complete(translateError(error))
              }
            }
          }
        }
      }
    }

  private def createSignInResponse(signInResponse: Future[Either[SignInError, AccessToken]]) = {
    onSuccess(signInResponse) {
      case Right(accessToken) => complete(AccessTokenResponse.fromDomain(accessToken))
      case Left(signInError) => complete(translateError(signInError))
    }
  }

  private def translateError(signInError: SignInError): (StatusCodes.ClientError, ErrorResponse) = signInError match {
    case SignInError.InvalidCredentials =>
      StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue, "Invalid credentials")
  }

  private def translateError(error: GetUserError): (StatusCodes.ClientError, ErrorResponse) = error match {
    case GetUserError.UserNotFound =>
      StatusCodes.NotFound -> ErrorResponse(StatusCodes.NotFound.intValue, "User not found")
  }

  private def translateError(error: PasswordResetError): (StatusCodes.ClientError, ErrorResponse) = error match {
    case PasswordResetError.UserNotFound(email) =>
      StatusCodes.NotFound -> ErrorResponse(StatusCodes.NotFound.intValue, s"User not found for email : $email")
    case PasswordResetError.InvalidResetCode =>
      StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue, "Invalid reset code")
    case PasswordResetError.PasswordError(passwordError) => translateError(passwordError)
  }

  private def translateError(error: SignUpError): (StatusCodes.ClientError, ErrorResponse) = error match {
    case SignUpError.UsernameAlreadyTaken(username) =>
      StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue, s"User $username already exists")
    case SignUpError.EmailAlreadyTaken(email) =>
      StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue,
        s"User with email $email already registered")
  }

  private def translateError(error: UpdatePasswordError): (StatusCodes.ClientError, ErrorResponse) = error match {
    case UpdatePasswordError.InvalidOldPassword =>
      StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue, "Invalid old password")
    case UpdatePasswordError.InvalidToken =>
      StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue, "Invalid token")
    case UpdatePasswordError.PasswordError(error) => translateError(error)
  }

  private def translateError(error: PasswordError): (StatusCodes.ClientError, ErrorResponse) = error match {
    case PasswordError.EmptyPassword =>
      StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue, "Empty password")
    case PasswordError.PreviouslyUsedPassword =>
      StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue, "Password previously used for user")
    case PasswordError.InvalidPasswordLength(message) =>
      StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue, message)
    case PasswordError.InvalidPasswordFormat(message) =>
      StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue, message)
  }

  private def translateError(error: UmAdminServiceError): (StatusCode, ErrorResponse) = error match {
    case UmAdminServiceError.AdminCannotDeactivateThemself =>
      errorResponse(StatusCodes.BadRequest, "Admin cannot deactivate themself")
    case UmAdminServiceError.AdminCannotDeleteThemself =>
      errorResponse(StatusCodes.BadRequest, "Admin cannot delete themself")
    case UmAdminServiceError.AdminCannotUpdateTheirRole =>
      errorResponse(StatusCodes.BadRequest, "Admin cannot update their role themself")
    case UmAdminServiceError.UserNotFound =>
      errorResponse(StatusCodes.NotFound, "User not found")
    case UmAdminServiceError.AccessDenied =>
      errorResponse(StatusCodes.Unauthorized, "User is not authorized")
    case UmAdminServiceError.UserHasAssets =>
      errorResponse(StatusCodes.BadRequest, "User has assets")
    case UmAdminServiceError.UsernameIsNotUnique(username) =>
      StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue, s"User $username already exists")
    case UmAdminServiceError.EmailIsNotUnique(email) =>
      StatusCodes.BadRequest -> ErrorResponse(StatusCodes.BadRequest.intValue,
        s"User with email $email already exists")
  }

  private def translateError(error: EmailConfirmationError): (StatusCode, ErrorResponse) = error match {
    case EmailConfirmationError.InvalidEmailConfirmationLink =>
      errorResponse(StatusCodes.BadRequest, "The email confirmation link you followed is invalid")
  }

}
