package baile.services.usermanagement

import java.time.ZonedDateTime
import java.util.UUID

import akka.event.LoggingAdapter
import akka.testkit.TestProbe
import baile.BaseSpec
import baile.domain.usermanagement.{ AccessToken, Role, UserStatus }
import baile.services.usermanagement.UMSentranaService._
import baile.services.usermanagement.UmService.PasswordError.{
  InvalidPasswordFormat,
  InvalidPasswordLength,
  PreviouslyUsedPassword
}
import baile.services.usermanagement.UmService.UmAdminServiceError.{
  AdminCannotDeactivateThemself,
  AdminCannotDeleteThemself,
  AdminCannotUpdateTheirRole
}
import baile.services.usermanagement.UmService.UpdatePasswordError.{ InvalidOldPassword, InvalidToken }
import baile.services.usermanagement.UmService._
import baile.services.usermanagement.datacontract._
import baile.services.usermanagement.exceptions.UnexpectedResponseException
import baile.services.usermanagement.util.TestData
import baile.services.usermanagement.util.TestData._
import baile.utils.MailService
import cats.implicits._
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito._

import scala.concurrent.ExecutionContext
import scala.util.Success

class UmServiceSpec extends BaseSpec {

  val umSentranaService = TestProbe()
  val mailService = mock[MailService]
  val loggingAdapter = mock[LoggingAdapter]
  val registry = mock[OwnershipTransferRegistry]
  val umService = new UmService(registry, conf, umSentranaService.ref, mailService, loggingAdapter)

  implicit val sampleUser = TestData.SampleAdmin


  val zonedDateTimeNow = ZonedDateTime.now
  val created = zonedDateTimeNow.minusDays(2)
  val updated = zonedDateTimeNow.minusHours(3)


  val sampleUserResponse = UserResponse(
    id = SampleUser.id,
    username = SampleUser.username,
    email = SampleUser.email,
    firstName = SampleUser.firstName,
    lastName = SampleUser.lastName,
    status = UserStatus.Active,
    fromRootOrg = false,
    created = TestData.DateAndTime,
    updated = TestData.DateAndTime,
    permissions = Seq(),
    userGroupIds = Set()
  )

  val userCreationResponse = SampleUser.id

  val userResponse = UserResponse(
    id = SampleUser.id,
    username = SampleUser.username,
    email = SampleUser.email,
    firstName = SampleUser.firstName,
    lastName = SampleUser.lastName,
    status = UserStatus.Active,
    fromRootOrg = false,
    created = TestData.DateAndTime,
    updated = TestData.DateAndTime,
    permissions = Seq(),
    userGroupIds = Set()
  )

  val errorResponse = ErrorResponse("Server Timeout", None)

  val token = "token"
  val password = "pwd"

  "UmService#signOut" should {
    "sign user out" in {
      val result = umService.signOut(token)
      umSentranaService.expectMsg(SignOut(token))
      umSentranaService.reply(())
      result.futureValue
    }
  }

  "UmService#signIn" should {

    val tokenExpires = 42
    val tokenType = "ttype"

    "sign user in" in {
      whenReady {
        val result = umService.signIn(SampleUser.username, password)
        umSentranaService.expectMsg(SignIn(SampleUser.username, password))
        umSentranaService.reply(TokenResponse(token, tokenExpires, tokenType).asRight)
        result
      }(_ shouldBe AccessToken(token, tokenExpires, tokenType).asRight)
    }

    "return invalid credentials error" in {
      whenReady {
        val result = umService.signIn(SampleUser.username, password)
        umSentranaService.expectMsg(SignIn(SampleUser.username, password))
        umSentranaService.reply(ErrorResponse("invalid_grant", None).asLeft)
        result
      }(_ shouldBe SignInError.InvalidCredentials.asLeft)
    }

    "throw unexpected error exception" in {
      whenReady {
        val result = umService.signIn(SampleUser.username, password)
        umSentranaService.expectMsg(SignIn(SampleUser.username, password))
        umSentranaService.reply(ErrorResponse("NO", None).asLeft)
        result.failed
      }(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "UmService#signUp" should {

    "sign user up" in {
      whenReady {
        val result = umService.signUp(
          username = SampleUser.username,
          email = SampleUser.email,
          password = password,
          firstName = SampleUser.firstName,
          lastName = SampleUser.lastName
        )
        umSentranaService.expectMsg(SignUp(
          username = SampleUser.username,
          email = SampleUser.email,
          password = password,
          firstName = SampleUser.firstName,
          lastName = SampleUser.lastName,
          requireEmailConfirmation = true
        ))
        umSentranaService.reply(SignUpResponse(SampleUser.id, "Welcome to Sentrana!").asRight)
        umSentranaService.expectMsg(GetUser(SampleUser.id))
        umSentranaService.reply(sampleUserResponse.asRight)
        result
      }(_ shouldBe SampleUser.asRight)
    }

    "return email is already taken error" in {
      whenReady {
        val result = umService.signUp(
          username = SampleUser.username,
          email = SampleUser.email,
          password = password,
          firstName = SampleUser.firstName,
          lastName = SampleUser.lastName
        )
        umSentranaService.expectMsg(SignUp(
          username = SampleUser.username,
          email = SampleUser.email,
          password = password,
          firstName = SampleUser.firstName,
          lastName = SampleUser.lastName,
          requireEmailConfirmation = true
        ))
        umSentranaService.reply(ErrorResponse(s"User with email ${SampleUser.email} already registered", None).asLeft)
        result
      }(_ shouldBe SignUpError.EmailAlreadyTaken(SampleUser.email).asLeft)
    }

    "return username is already taken error" in {
      whenReady {
        val result = umService.signUp(
          username = SampleUser.username,
          email = SampleUser.email,
          password = password,
          firstName = SampleUser.firstName,
          lastName = SampleUser.lastName
        )
        umSentranaService.expectMsg(SignUp(
          username = SampleUser.username,
          email = SampleUser.email,
          password = password,
          firstName = SampleUser.firstName,
          lastName = SampleUser.lastName,
          requireEmailConfirmation = true
        ))
        umSentranaService.reply(ErrorResponse(s"User ${SampleUser.username} already exists", None).asLeft)
        result
      }(_ shouldBe SignUpError.UsernameAlreadyTaken(SampleUser.username).asLeft)
    }

    "throw unexpected error exception" in {
      whenReady {
        val result = umService.signUp(
          username = SampleUser.username,
          email = SampleUser.email,
          password = password,
          firstName = SampleUser.firstName,
          lastName = SampleUser.lastName
        )
        umSentranaService.expectMsg(SignUp(
          username = SampleUser.username,
          email = SampleUser.email,
          password = password,
          firstName = SampleUser.firstName,
          lastName = SampleUser.lastName,
          requireEmailConfirmation = true
        ))
        umSentranaService.reply(ErrorResponse("NO", None).asLeft)
        result.failed
      }(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "UmService#initiatePasswordReset" should {
    "initiate password reset procedure" in {
      val result = umService.initiatePasswordReset(SampleUser.email)
      umSentranaService.expectMsg(InitiatePasswordReset(SampleUser.email))
      umSentranaService.reply(())
      result.futureValue
    }
  }

  val newPassword = "newpwd"

  "UmService#resetPassword" should {

    val secretCode = "secret"

    "set new password for user" in {
      whenReady {
        val result = umService.resetPassword(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = newPassword
        )
        umSentranaService.expectMsg(CompletePasswordReset(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = newPassword
        ))
        umSentranaService.reply(().asRight)
        result
      }(_ shouldBe ().asRight)
    }

    "return invalid reset code error" in {
      whenReady {
        val result = umService.resetPassword(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = newPassword
        )
        umSentranaService.expectMsg(CompletePasswordReset(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = newPassword
        ))
        umSentranaService.reply(ErrorResponse(s"Password reset code $secretCode is not valid for user", None).asLeft)
        result
      }(_ shouldBe PasswordResetError.InvalidResetCode.asLeft)
    }

    "return username is already taken error" in {
      whenReady {
        val result = umService.resetPassword(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = newPassword
        )
        umSentranaService.expectMsg(CompletePasswordReset(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = newPassword
        ))
        umSentranaService.reply(ErrorResponse(s"No user with email ${SampleUser.email} were found", None).asLeft)
        result
      }(_ shouldBe PasswordResetError.UserNotFound(SampleUser.email).asLeft)
    }

    "return empty password error" in {
      whenReady {
        val result = umService.resetPassword(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = ""
        )
        umSentranaService.expectMsg(CompletePasswordReset(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = ""
        ))
        umSentranaService.reply(ErrorResponse("Empty password for user", None).asLeft)
        result
      }(_ shouldBe PasswordResetError.PasswordError(PasswordError.EmptyPassword).asLeft)
    }

    "return invalid password length error" in {
      val message = s"Invalid passwords length for user ${SampleUser.username}. Possible lengths are from 10 to 50"
      whenReady {
        val result = umService.resetPassword(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = password.take(5)
        )
        umSentranaService.expectMsg(CompletePasswordReset(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = password.take(5)
        ))
        umSentranaService.reply(ErrorResponse(message, None).asLeft)
        result
      }(_ shouldBe PasswordResetError.PasswordError(InvalidPasswordLength(message)).asLeft)
    }

    "return previously used password error" in {
      whenReady {
        val result = umService.resetPassword(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = password
        )
        umSentranaService.expectMsg(CompletePasswordReset(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = password
        ))
        umSentranaService.reply(ErrorResponse("Password previously used for user", None).asLeft)
        result
      }(_ shouldBe PasswordResetError.PasswordError(PreviouslyUsedPassword).asLeft)
    }

    "return invalid password format error" in {
      val message = "Password must contain at least 3 out of 4 character types:" +
        " uppercase letter, lowercase letter, special character, digit"
      whenReady {
        val result = umService.resetPassword(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = newPassword
        )
        umSentranaService.expectMsg(CompletePasswordReset(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = newPassword
        ))
        umSentranaService.reply(ErrorResponse(message, None).asLeft)
        result
      }(_ shouldBe PasswordResetError.PasswordError(InvalidPasswordFormat(message)).asLeft)
    }

    "throw unexpected error exception" in {
      whenReady {
        val result = umService.resetPassword(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = password
        )
        umSentranaService.expectMsg(CompletePasswordReset(
          email = SampleUser.email,
          secretCode = secretCode,
          newPassword = password
        ))
        umSentranaService.reply(ErrorResponse("NO", None).asLeft)
        result.failed
      }(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "UmService#activateUser" should {

    "activate the user on the basis of user id" in {
      whenReady {
        val result = umService.activateUser(SampleUser.id)(SampleAdmin)
        umSentranaService.expectMsg(ActivateUser(SampleUser.id))
        umSentranaService.reply(userResponse.asRight
        )
        result
      }(_ shouldBe SampleUser.asRight)
    }

    "not be able to activate the user if not user is not found" in {
      whenReady {
        val result = umService.activateUser(SampleUser.id)(SampleAdmin)
        umSentranaService.expectMsg(ActivateUser(SampleUser.id))
        umSentranaService.reply(ErrorResponse("User not found", None).asLeft)
        result
      }(_ shouldBe UmAdminServiceError.UserNotFound.asLeft)
    }

  }

  "UmService#deactivateUser" should {

    "deactivate the user on the basis of user id" in {
      whenReady {
        val result = umService.deactivateUser(SampleUser.id)(SampleAdmin)
        umSentranaService.expectMsg(DeactivateUser(SampleUser.id, true))
        umSentranaService.reply(userResponse.asRight)
        result
      }(_ shouldBe SampleUser.asRight)
    }

    "return exception when admin tries to deactivate themself" in {
      whenReady {
        val result = umService.deactivateUser(
          SampleAdmin.id
        )(SampleAdmin)
        result
      }(_ shouldBe AdminCannotDeactivateThemself.asLeft)
    }

    "not be able to deactivate the user if not user is not found" in {
      whenReady {
        val result = umService.deactivateUser(SampleUser.id)(SampleAdmin)
        umSentranaService.expectMsg(DeactivateUser(SampleUser.id, true))
        umSentranaService.reply(ErrorResponse("User not found", None).asLeft)
        result
      }(_ shouldBe UmAdminServiceError.UserNotFound.asLeft)
    }

  }

  "UmService#remindUsername" should {

    "send email when user was found" in {
      val email = "myemail@example.com"
      when(mailService.sendHtmlFormattedEmail(
        any[String],
        any[String],
        any[String],
        any[String]
      )).thenReturn(Success(()))
      val result = umService.remindUsername(email)
      umSentranaService.expectMsg(FindUsers(
        firstName = None,
        lastName = None,
        username = None,
        email = Some(email),
        emailPrefix = None,
        orgId = None,
        offset = 0,
        limit = 10
      ))
      umSentranaService.reply(ListResponse(Seq(sampleUserResponse), 0, 1).asRight)
      result.futureValue
      verify(mailService).sendHtmlFormattedEmail(
        any[String],
        any[String],
        any[String],
        any[String]
      )
    }

    "do nothing when user was not found" in {
      val email = "nonexist@example.com"
      val result = umService.remindUsername(email)
      umSentranaService.expectMsg(FindUsers(
        firstName = None,
        lastName = None,
        username = None,
        email = Some(email),
        emailPrefix = None,
        orgId = None,
        offset = 0,
        limit = 10
      ))
      umSentranaService.reply(ListResponse(Seq.empty, 0, 0).asRight)
      result.futureValue
    }

  }

  "UmService#getUser" should {

    "return user" in {
      whenReady {
        val result = umService.getUser(SampleUser.id)
        umSentranaService.expectMsg(GetUser(SampleUser.id))
        umSentranaService.reply(sampleUserResponse.asRight)
        result
      }(_ shouldBe SampleUser.asRight)
    }

    "return user not found error" in {
      whenReady {
        val result = umService.getUser(SampleUser.id)
        umSentranaService.expectMsg(GetUser(SampleUser.id))
        umSentranaService.reply(ErrorResponse("user not found", None).asLeft)
        result
      }(_ shouldBe GetUserError.UserNotFound.asLeft)
    }

    "throw unexpected error exception" in {
      whenReady {
        val result = umService.getUser(SampleUser.id)
        umSentranaService.expectMsg(GetUser(SampleUser.id))
        umSentranaService.reply(ErrorResponse("NO", None).asLeft)
        result.failed
      }(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "UmService#findUsers" should {
    "return list of users" in {
      whenReady {
        val prefix = Some("prefix")
        val result = umService.findUsers(emailPrefix = prefix)
        umSentranaService.expectMsg(FindUsers(
          firstName = None,
          lastName = None,
          username = None,
          email = None,
          emailPrefix = prefix,
          orgId = None,
          offset = 0,
          limit = 10
        ))
        umSentranaService.reply(
          ListResponse(Seq(sampleUserResponse, sampleUserResponse), 0, 2).asRight)
        result
      }(_ shouldBe List(SampleUser, SampleUser))
    }
  }

  "UmService#validateAccessToken" should {

    "return user" in {
      whenReady {
        val result = umService.validateAccessToken(token)
        umSentranaService.expectMsg(ValidateAccessToken(token))
        umSentranaService.reply(sampleUserResponse.asRight)
        result
      }(_ shouldBe SampleUser.asRight)
    }

    "return user not found error" in {
      whenReady {
        val result = umService.validateAccessToken(token)
        umSentranaService.expectMsg(ValidateAccessToken(token))
        umSentranaService.reply(ErrorResponse("invalid_grant", Some("No such token")).asLeft)
        result
      }(_ shouldBe GetUserError.UserNotFound.asLeft)
    }

    "throw unexpected error exception" in {
      whenReady {
        val result = umService.validateAccessToken(token)
        umSentranaService.expectMsg(ValidateAccessToken(token))
        umSentranaService.reply(ErrorResponse("NO", None).asLeft)
        result.failed
      }(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "UmService#updatePassword" should {

    "change password for user" in {
      whenReady {
        val result = umService.updatePassword(token, password, newPassword)
        umSentranaService.expectMsg(UpdatePassword(token, password, newPassword))
        umSentranaService.reply(().asRight)
        result
      }(_ shouldBe ().asRight)
    }

    "return invalid old password error" in {
      whenReady {
        val result = umService.updatePassword(token, password, newPassword)
        umSentranaService.expectMsg(UpdatePassword(token, password, newPassword))
        umSentranaService.reply(ErrorResponse("Old password doesn't match", None).asLeft)
        result
      }(_ shouldBe InvalidOldPassword.asLeft)
    }

    "return invalid token error" in {
      whenReady {
        val result = umService.updatePassword(token, password, newPassword)
        umSentranaService.expectMsg(UpdatePassword(token, password, newPassword))
        umSentranaService.reply(ErrorResponse("Forbidden", None).asLeft)
        result
      }(_ shouldBe InvalidToken.asLeft)
    }

    "return previously used password error" in {
      whenReady {
        val result = umService.updatePassword(token, password, password)
        umSentranaService.expectMsg(UpdatePassword(token, password, password))
        umSentranaService.reply(ErrorResponse("Password previously used for user", None).asLeft)
        result
      }(_ shouldBe UpdatePasswordError.PasswordError(PreviouslyUsedPassword).asLeft)
    }

    "throw unexpected error exception" in {
      whenReady {
        val result = umService.updatePassword(token, password, password)
        umSentranaService.expectMsg(UpdatePassword(token, password, password))
        umSentranaService.reply(ErrorResponse("NO", None).asLeft)
        result.failed
      }(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "UmService#fetchUsers" should {

    "find all existing users" in {
      whenReady {
        val result = umService.fetchUsers(
          None,
          None,
          None,
          1,
          10
        )(SampleAdmin)
        umSentranaService.expectMsg(FindUsers(
          None,
          None,
          None,
          None,
          None,
          None,
          0,
          10
        ))
        umSentranaService.reply(
          ListResponse(Seq(userResponse), 0, 1).asRight
        )
        result
      }(_ shouldBe (Seq(SampleUser), 1).asRight)
    }

    "restrict access if the user does not have super user access" in {
      whenReady {
        umService.fetchUsers(
          None,
          None,
          None,
          1,
          10
        )(SampleUser)
      }(_ shouldBe UmAdminServiceError.AccessDenied.asLeft)
    }

  }

  "UmService#createUser" should {
    "create the user details" in {
      whenReady {
        val result = umService.createUser(
          SampleUser.username,
          SampleUser.email,
          password,
          SampleUser.firstName,
          SampleUser.lastName,
          SampleUser.role
        )(SampleAdmin)
        umSentranaService.expectMsg(
          CreateUser(
            SampleUser.username,
            SampleUser.email,
            password,
            SampleUser.firstName,
            SampleUser.lastName,
            SampleUser.role,
            true
          )
        )
        umSentranaService.reply(sampleUserResponse.asRight)
        result
      }(_ shouldBe SampleUser.asRight)
    }

    "throw unexpected error exception" in {
      whenReady {
        val result = umService.createUser(
          SampleUser.username,
          SampleUser.email,
          password,
          SampleUser.firstName,
          SampleUser.lastName,
          SampleUser.role
        )(SampleAdmin)
        umSentranaService.expectMsg(CreateUser(SampleUser.username, SampleUser.email, password, SampleUser.firstName, SampleUser.lastName, SampleUser.role, true))
        umSentranaService.reply(errorResponse.asLeft)
        result.failed
      }(_ shouldBe an[UnexpectedResponseException])
    }

    "restrict access if the user does not have super user access" in {
      whenReady {
        umService.createUser(
          SampleUser.username,
          SampleUser.email,
          password,
          SampleUser.firstName,
          SampleUser.lastName,
          SampleUser.role
        )(SampleUser)
      }(_ shouldBe UmAdminServiceError.AccessDenied.asLeft)
    }

    "return email is not unique error" in {
      whenReady {
        val result = umService.createUser(
          SampleUser.username,
          SampleUser.email,
          password,
          SampleUser.firstName,
          SampleUser.lastName,
          SampleUser.role
        )
        umSentranaService.expectMsg(CreateUser(
          username = SampleUser.username,
          email = SampleUser.email,
          password = password,
          firstName = SampleUser.firstName,
          lastName = SampleUser.lastName,
          role = SampleUser.role,
          requireEmailConfirmation = true
        ))
        umSentranaService.reply(ErrorResponse(s"User email ${SampleUser.email} is not unique", None).asLeft)
        result
      }(_ shouldBe UmAdminServiceError.EmailIsNotUnique(SampleUser.email).asLeft)
    }

    "return username is not unique error" in {
      whenReady {
        val result = umService.createUser(
          SampleUser.username,
          SampleUser.email,
          password,
          SampleUser.firstName,
          SampleUser.lastName,
          SampleUser.role
        )
        umSentranaService.expectMsg(CreateUser(
          username = SampleUser.username,
          email = SampleUser.email,
          password = password,
          firstName = SampleUser.firstName,
          lastName = SampleUser.lastName,
          role = SampleUser.role,
          requireEmailConfirmation = true
        ))
        umSentranaService.reply(ErrorResponse(s"User name ${SampleUser.username} is not unique", None).asLeft)
        result
      }(_ shouldBe UmAdminServiceError.UsernameIsNotUnique(SampleUser.username).asLeft)
    }

  }

  "UmService#deleteUser" should {
    val transferToId = UUID.randomUUID()
    when(registry.getAllAssetCount(eqTo(SampleUser.id))(any[ExecutionContext]))
      .thenReturn(future(1))
    when(registry.transferOwnership(eqTo(SampleUser.id), eqTo(transferToId))(any[ExecutionContext]))
      .thenReturn(future(()))

    "delete user" in {
      whenReady {
        val result = umService.deleteUser(
          SampleUser.id,
          Some(transferToId)
        )(SampleAdmin)
        umSentranaService.expectMsg(
          DeleteUser(SampleUser.id)
        )
        umSentranaService.reply(().asRight)
        result
      }(_ shouldBe ().asRight)
    }

    "return exception when admin tries to delete themself" in {
      whenReady {
        val result = umService.deleteUser(
          SampleAdmin.id,
          Some(transferToId)
        )(SampleAdmin)
        result
      }(_ shouldBe AdminCannotDeleteThemself.asLeft)
    }

    "throw unexpected error exception" in {
      whenReady {
        val result = umService.deleteUser(
          SampleUser.id,
          Some(transferToId)
        )(SampleAdmin)
        umSentranaService.expectMsg(
          DeleteUser(SampleUser.id)
        )
        umSentranaService.reply(errorResponse.asLeft)
        result.failed
      }(_ shouldBe an[UnexpectedResponseException])
    }

    "return user not found if user does not exists" in {
      whenReady {
        val result = umService.deleteUser(
          SampleUser.id,
          Some(transferToId)
        )(SampleAdmin)
        umSentranaService.expectMsg(
          DeleteUser(SampleUser.id)
        )
        umSentranaService.reply(errorResponse.copy(error = "user not found").asLeft)
        result
      }(_ shouldBe UmAdminServiceError.UserNotFound.asLeft)
    }
  }

  "UmService#fetchUser" should {
    "fetch user details" in {
      whenReady {
        val result = umService.fetchUser(
          SampleUser.id
        )(SampleAdmin)
        umSentranaService.expectMsg(
          GetUser(SampleUser.id)
        )
        umSentranaService.reply(sampleUserResponse.asRight)
        result
      }(_ shouldBe SampleUser.asRight)
    }

    "throw unexpected error exception" in {
      whenReady {
        val result = umService.fetchUser(
          SampleUser.id
        )(SampleAdmin)
        umSentranaService.expectMsg(
          GetUser(SampleUser.id)
        )
        umSentranaService.reply(errorResponse.asLeft)
        result.failed
      }(_ shouldBe an[UnexpectedResponseException])
    }

    "return user not found if user does not exists" in {
      whenReady {
        val result = umService.fetchUser(
          SampleUser.id
        )(SampleAdmin)
        umSentranaService.expectMsg(
          GetUser(SampleUser.id)
        )
        umSentranaService.reply(errorResponse.copy(error = "user not found").asLeft)
        result
      }(_ shouldBe UmAdminServiceError.UserNotFound.asLeft)
    }
  }

  "UmService#updateUser" should {
    "update the user details" in {
      whenReady {
        val result = umService.updateUser(userId = SampleUser.id, username = Some("new-username"))(SampleAdmin)
        umSentranaService.expectMsg(UpdateUser(
          SampleUser.id,
          Some("new-username"),
          None,
          None,
          None,
          None,
          None
        ))
        umSentranaService.reply(sampleUserResponse.asRight)
        result
      }(_ shouldBe SampleUser.asRight)
    }

    "return error when tries to update their role" in {
      whenReady {
        val result = umService.updateUser(userId = SampleAdmin.id, role = Some(Role.User))(SampleAdmin)
        result
      }(_ shouldBe AdminCannotUpdateTheirRole.asLeft)
    }

    "throw unexpected error exception" in {
      whenReady {
        val result = umService.updateUser(
          SampleUser.id,
          Some(SampleUser.username),
          None,
          None,
          None,
          None,
          None
        )(SampleAdmin)
        umSentranaService.expectMsg(UpdateUser(
          SampleUser.id,
          Some(SampleUser.username),
          None,
          None,
          None,
          None,
          None
        ))
        umSentranaService.reply(errorResponse.asLeft)
        result.failed
      }(_ shouldBe an[UnexpectedResponseException])
    }

    "restrict access if the user does not have super user access" in {
      whenReady {
        umService.updateUser(SampleUser.id, Some(SampleUser.username), None, None, None, None)(SampleUser)
      }(_ shouldBe UmAdminServiceError.AccessDenied.asLeft)
    }

    "return email is not unique error" in {
      whenReady {
        val result = umService.updateUser(
          userId = SampleUser.id,
          username = Some("new-username"),
          email = Some("old-email")
        )(SampleAdmin)
        umSentranaService.expectMsg(UpdateUser(
          SampleUser.id,
          Some("new-username"),
          Some("old-email"),
          None,
          None,
          None,
          None
        ))
        umSentranaService.reply(ErrorResponse(s"User email old-email is not unique", None).asLeft)
        result
      }(_ shouldBe UmAdminServiceError.EmailIsNotUnique("old-email").asLeft)
    }

    "return username is not unique error" in {
      whenReady {
        val result = umService.updateUser(
          userId = SampleUser.id,
          username = Some("new-username")
        )(SampleAdmin)
        umSentranaService.expectMsg(UpdateUser(
          SampleUser.id,
          Some("new-username"),
          None,
          None,
          None,
          None,
          None
        ))
        umSentranaService.reply(ErrorResponse(s"User name new-username is not unique", None).asLeft)
        result
      }(_ shouldBe UmAdminServiceError.UsernameIsNotUnique("new-username").asLeft)
    }

  }

  "UmService#confirmUser" should {
    val orgId = "orgId"

    "confirm user email returning login url" in {
      val activationCode = UUID.randomUUID()

      whenReady {
        val result = umService.confirmUser(
          orgId = orgId,
          SampleUser.id,
          activationCode = activationCode
        )

        umSentranaService.expectMsg(ConfirmUser(orgId, SampleUser.id, activationCode))
        umSentranaService.reply(ConfirmUserResponse("").asRight)

        result
      }(_ shouldBe ().asRight)
    }

    "not be able to confirm user email if an activation code is invalid" in {
      val activationCode = UUID.randomUUID()
      whenReady {
        val result = umService.confirmUser(
          orgId = orgId,
          SampleUser.id,
          activationCode = activationCode
        )

        umSentranaService.expectMsg(ConfirmUser(orgId, SampleUser.id, activationCode))
        umSentranaService.reply(ErrorResponse("The email confirmation link you followed is invalid", None).asLeft)

        result
      }(_ shouldBe EmailConfirmationError.InvalidEmailConfirmationLink.asLeft)
    }

  }
}
