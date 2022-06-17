package baile.routes.usermanagement

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{ Authorization, GenericHttpCredentials }
import akka.http.scaladsl.server.Route
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.routes.usermanagement.util.TestData
import baile.routes.usermanagement.util.TestData._
import baile.services.common.AuthenticationService
import baile.services.usermanagement.UmService
import baile.services.usermanagement.UmService.EmailConfirmationError.InvalidEmailConfirmationLink
import baile.services.usermanagement.UmService.SignInError.InvalidCredentials
import baile.services.usermanagement.UmService._
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.mockito.Mockito._
import play.api.libs.json.{ JsObject, JsString, Json }

class UMRoutesSpec extends RoutesSpec {

  private val service = mock[UmService]
  private val authenticationService = mock[AuthenticationService]
  implicit val user: User = Admin
  val routes: Route = new UMRoutes(service, authenticationService, conf).routes

  "POST /signin" should {

    "return SUCCESS response from signin when user signIn by username " in {
      when(service.signIn("username", "password")).thenReturn(future(AccessTokenSample.asRight))

      Post("/signin", Json.parse(TestData.SignInWithUserNameParametersRequestJson)).check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe SignInResponse
      }
    }

    "return error response from signin when user signIn by username" in {
      when(service.signIn("username", "password")).thenReturn(future(InvalidCredentials.asLeft))

      Post("/signin", Json.parse(TestData.SignInWithUserNameParametersRequestJson)).check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

    "return SUCCESS response from signin when user signin by email " in {
      when(service.signIn("email", "password", isEmail = true)).thenReturn(future(AccessTokenSample.asRight))

      Post("/signin", Json.parse(TestData.SignInWithEmailParametersRequestJson)).check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe SignInResponse
      }
    }

    "return error response from signin when user signIn by email" in {
      when(service.signIn("email", "password", isEmail = true)).thenReturn(future(InvalidCredentials.asLeft))

      Post("/signin", Json.parse(TestData.SignInWithEmailParametersRequestJson)).check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

    "return error response from signin when neither username not email provided" in {
      Post("/signin", Json.parse(TestData.SignInWithPasswordOnlyRequestJson)).check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "GET /me" should {

    val authorizationHeader = Authorization(GenericHttpCredentials("Bearer", "token"))

    "return SUCCESS response" in {
      when(service.getUser(SampleUser.id)).thenReturn(future(SampleUser.asRight))
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(SampleUser)))

      Get("/me").withHeaders(authorizationHeader).check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe UserResponse
      }
    }

    "return Error response if token exist in query paramter" in
      Get("/me?access_token=token").check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[JsObject].keys should contain allOf("code", "message")
      }

    "return Error response when user not found on provided token" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(None))

      Get("/me").withHeaders(authorizationHeader).check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

    "return Error response when token does not exist in request" in {
      Get("/me").check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

    "return error response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(SampleUser)))
      when(service.getUser(SampleUser.id)).thenReturn(future(GetUserError.UserNotFound.asLeft))

      Get("/me").signed.check {
        status shouldEqual StatusCodes.NotFound
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "POST /me/password" should {

    "return SUCCESS response" in {
      when(service.updatePassword(userToken, "oldPassword", "newPassword"))
        .thenReturn(future(().asRight))
      Post("/me/password", Json.parse(ChangePasswordRequestJson)).signed.check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return InvalidToken error" in {
      when(service.updatePassword(userToken, "oldPassword", "newPassword"))
        .thenReturn(future(UpdatePasswordError.InvalidToken.asLeft))

      Post("/me/password", Json.parse(ChangePasswordRequestJson)).signed.check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

    "return UmServiceError error" in {
      when(service.updatePassword(userToken, "oldPassword", ""))
        .thenReturn(future(UpdatePasswordError.PasswordError(PasswordError.EmptyPassword).asLeft))

      Post("/me/password", Json.parse(ChangePasswordRequestJson)).signed.check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

  }

  "POST /me/password/reset" should {

    "return SUCCESS response" in {
      when(service.initiatePasswordReset("email")).thenReturn(future(()))

      Post("/me/password/reset", Json.parse(ResetPasswordRequestJson)).check {
        status shouldEqual StatusCodes.Accepted
      }
    }

  }

  "POST /me/password/resetcomplete" should {

    "return SUCCESS response" in {
      when(service.resetPassword("email", "secretCode", "newPassword")).thenReturn(future(().asRight))

      Post("/me/password/resetcomplete", Json.parse(ResetPasswordCompleteRequestSampleJson)).check {
        status shouldEqual StatusCodes.OK
      }
    }

    "return InvalidResetCode error" in {
      when(service.resetPassword("email", "secretCode", "newPassword")) thenReturn
        future(PasswordResetError.InvalidResetCode.asLeft)

      Post("/me/password/resetcomplete", Json.parse(ResetPasswordCompleteRequestSampleJson)).check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

    "return UserNotFound error" in {
      when(service.resetPassword("email", "secretCode", "newPassword")) thenReturn
        future(PasswordResetError.UserNotFound("email").asLeft)

      Post("/me/password/resetcomplete", Json.parse(ResetPasswordCompleteRequestSampleJson)).check {
        status shouldEqual StatusCodes.NotFound
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "POST /me/username/remind" should {

    "return SUCCESS response" in {
      when(service.remindUsername(email = "email")).thenReturn(future(()))

      Post("/me/username/remind", Json.parse(RemindUsernameRequestJson)).check {
        status shouldEqual StatusCodes.Accepted
      }
    }
  }

  "POST /signup" should {

    "return SUCCESS response" in {
      when(service.signUp("username", "email", "password", "firstName", "lastName")) thenReturn
        future(SampleUser.asRight)

      Post("/signup", Json.parse(SignUpRequestJson)).check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe UserResponse
      }
    }

    "return UsernameAlreadyTaken error" in {
      when(service.signUp("username", "email", "password", "firstName", "lastName")) thenReturn
        future(SignUpError.UsernameAlreadyTaken("username").asLeft)

      Post("/signup", Json.parse(SignUpRequestJson)).check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

    "return EmailAlreadyTaken error" in {
      when(service.signUp("username", "email", "password", "firstName", "lastName")) thenReturn
        future(SignUpError.EmailAlreadyTaken("email").asLeft)

      Post("/signup", Json.parse(SignUpRequestJson)).check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "POST /emailconfirmation" should {
    val userId = UUID.randomUUID()
    val activationCode = UUID.randomUUID()
    val emailConfirmationRequest = Json.obj(
      "orgId" -> JsString("orgId"),
      "userId" -> JsString(userId.toString),
      "activationCode" -> JsString(activationCode.toString)
    )
    "successfully redirect" in {
      when(service.confirmUser("orgId", userId, activationCode)) thenReturn future(().asRight)
      Post("/emailconfirmation", emailConfirmationRequest).check {
        status shouldBe StatusCodes.Accepted
      }
    }
    
    "return InvalidEmailConfirmationLink error" in {
      when(service.confirmUser("orgId", userId, activationCode)) thenReturn future(InvalidEmailConfirmationLink.asLeft)
      Post("/emailconfirmation", emailConfirmationRequest).check {
         status shouldBe StatusCodes.BadRequest
         responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "POST /signout" should {

    "return SUCCESS response" in {
      when(service.signOut("token")) thenReturn
        future(())

      Post("/signout").signed.check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  "GET /users" should {

    "return SUCCESS response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.fetchUsers(
        None,
        None,
        None,
        1,
        2
      )) thenReturn future((Seq(SampleUser), 1L).asRight)

      Get("/users?page=1&page_size=2").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe ListUserResponse
      }
    }

    "return error response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.fetchUsers(
        None,
        None,
        None,
        1,
        2
      )) thenReturn future(UmAdminServiceError.UserNotFound.asLeft)

      Get("/users?page=1&page_size=2").signed.check {
        status shouldEqual StatusCodes.NotFound
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

  }

  "GET /users/:id" should {

    "return SUCCESS response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.fetchUser(Admin.id)) thenReturn future(Admin.asRight)
      Get(s"/users/${ Admin.id.toString }").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe AdminResponse
      }
    }

    "return Error response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.fetchUser(Admin.id)) thenReturn future(UmAdminServiceError.UserNotFound.asLeft)
      Get(s"/users/${ Admin.id.toString }").signed.check {
        status shouldEqual StatusCodes.NotFound
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "DELETE /users/:id" should {

    val toId = UUID.randomUUID()

    "return SUCCESS response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.deleteUser(Admin.id, Some(toId))) thenReturn future(().asRight)
      Delete(s"/users/${ Admin.id.toString }?transferOwnershipTo=${ toId.toString }").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe IdResponse
      }
    }

    "return Error response" in {

      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.deleteUser(Admin.id, Some(toId))) thenReturn future(UmAdminServiceError.UserNotFound.asLeft)
      Delete(s"/users/${ Admin.id.toString }?transferOwnershipTo=${ toId.toString }").signed.check {
        status shouldEqual StatusCodes.NotFound
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

    "return Error response in case of access denied" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.deleteUser(Admin.id, Some(toId))) thenReturn future(UmAdminServiceError.AccessDenied.asLeft)
      Delete(s"/users/${ Admin.id.toString }?transferOwnershipTo=${ toId.toString }").signed.check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "POST /users/:id/activate" should {

    val authorizationHeader = Authorization(GenericHttpCredentials("Bearer", "token"))
    "return SUCCESS response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.activateUser(SampleUser.id)) thenReturn future(SampleUser.asRight)
      Post(s"/users/${ SampleUser.id.toString }/activate").withHeaders(authorizationHeader).check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe UserResponse
      }
    }

    "return Error response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.activateUser(SampleUser.id)) thenReturn future(UmAdminServiceError.UserNotFound.asLeft)
      Post(s"/users/${ SampleUser.id.toString }/activate").withHeaders(authorizationHeader).check {
        status shouldEqual StatusCodes.NotFound
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

    "return Error response in case of access denied" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.activateUser(SampleUser.id)) thenReturn future(UmAdminServiceError.AccessDenied.asLeft)
      Post(s"/users/${ SampleUser.id.toString }/activate").withHeaders(authorizationHeader).check {
        status shouldEqual StatusCodes.Unauthorized
      }
    }

  }

  "POST /users/:id/deactivate" should {

    val authorizationHeader = Authorization(GenericHttpCredentials("Bearer", "token"))
    "return SUCCESS response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.deactivateUser(SampleUser.id)(Admin)) thenReturn future(SampleUser.asRight)
      Post(s"/users/${ SampleUser.id.toString }/deactivate").withHeaders(authorizationHeader).check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe UserResponse
      }
    }

    "return Error response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.deactivateUser(SampleUser.id)) thenReturn future(UmAdminServiceError.UserNotFound.asLeft)
      Post(s"/users/${ SampleUser.id.toString }/deactivate").withHeaders(authorizationHeader).check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return Error response when access denied" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.deactivateUser(SampleUser.id)) thenReturn future(UmAdminServiceError.AccessDenied.asLeft)
      Post(s"/users/${ SampleUser.id.toString }/deactivate").withHeaders(authorizationHeader).check {
        status shouldEqual StatusCodes.Unauthorized
      }
    }

  }

  "PUT /users/:id" should {

    "return SUCCESS response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.updateUser(
        Admin.id,
        Some(CreateUserRequestByAdmin.username),
        Some(CreateUserRequestByAdmin.email),
        Some(CreateUserRequestByAdmin.password),
        Some(CreateUserRequestByAdmin.firstName),
        Some(CreateUserRequestByAdmin.lastName),
        None
      )) thenReturn future(SampleUser.asRight)
      Put(s"/users/${ Admin.id.toString }", Json.parse(UpdateUserRequest)).signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe UserResponse
      }
    }

    "return Error response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.updateUser(
        SampleUser.id,
        Some(CreateUserRequestByAdmin.username),
        Some(CreateUserRequestByAdmin.email),
        Some(CreateUserRequestByAdmin.password),
        Some(CreateUserRequestByAdmin.firstName),
        Some(CreateUserRequestByAdmin.lastName),
        None
      )) thenReturn future(UmAdminServiceError.AccessDenied.asLeft)
      Put(s"/users/${ SampleUser.id }", Json.parse(UpdateUserRequest)).signed.check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

    "return Error response when user not found" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.updateUser(
        SampleUser.id,
        Some(CreateUserRequestByAdmin.username),
        Some(CreateUserRequestByAdmin.email),
        Some(CreateUserRequestByAdmin.password),
        Some(CreateUserRequestByAdmin.firstName),
        Some(CreateUserRequestByAdmin.lastName),
        None
      )) thenReturn future(UmAdminServiceError.UserNotFound.asLeft)
      Put(s"/users/${ SampleUser.id }", Json.parse(UpdateUserRequest)).signed.check {
        status shouldEqual StatusCodes.NotFound
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }
  }

  "POST /users" should {

    val authorizationHeader = Authorization(GenericHttpCredentials("Bearer", userToken))
    "return SUCCESS response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.createUser(
        CreateUserRequestByAdmin.username,
        CreateUserRequestByAdmin.email,
        CreateUserRequestByAdmin.password,
        CreateUserRequestByAdmin.firstName,
        CreateUserRequestByAdmin.lastName,
        CreateUserRequestByAdmin.role
      )(Admin)) thenReturn future(SampleUser.asRight)

      Post("/users", Json.parse(CreateUserRequestJson)).withHeaders(authorizationHeader).check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject] shouldBe UserResponse
      }
    }

    "return bad request for incorrect json request" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.createUser(
        CreateUserRequestByAdmin.username,
        CreateUserRequestByAdmin.email,
        CreateUserRequestByAdmin.password,
        CreateUserRequestByAdmin.firstName,
        CreateUserRequestByAdmin.lastName,
        CreateUserRequestByAdmin.role
      )(Admin)) thenReturn future(SampleUser.asRight)
      Post("/users", Json.parse(InCorrectCreateUserRequestJson)).withHeaders(authorizationHeader).check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

    "return bad request for error response" in {
      when(authenticationService.authenticate(userToken)).thenReturn(future(Some(Admin)))
      when(service.createUser(
        CreateUserRequestByAdmin.username,
        CreateUserRequestByAdmin.email,
        CreateUserRequestByAdmin.password,
        CreateUserRequestByAdmin.firstName,
        CreateUserRequestByAdmin.lastName,
        CreateUserRequestByAdmin.role
      )(Admin)) thenReturn future(UmAdminServiceError.AccessDenied.asLeft)
      Post("/users", Json.parse(CreateUserRequestJson)).withHeaders(authorizationHeader).check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[JsObject].keys should contain allOf("code", "message")
      }
    }

  }

}
