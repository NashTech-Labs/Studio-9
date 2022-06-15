package baile.services.usermanagement

import java.time.ZonedDateTime
import java.util.UUID

import akka.actor.{ ActorRef, Props }
import akka.event.LoggingAdapter
import akka.http.caching.scaladsl.Cache
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.pattern.ask
import baile.BaseSpec
import baile.domain.usermanagement.UserStatus
import baile.services.http.exceptions.UnexpectedResponseException
import baile.services.usermanagement.UMSentranaService._
import baile.services.usermanagement.datacontract._
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.libs.json._

import scala.concurrent.Future

class UMSentranaServiceSpec extends BaseSpec {

  val cache = mock[Cache[String, UserResponse]]

  def umSentranaService: ActorRef = system.actorOf(Props(new UMSentranaService(conf, httpMock, cache)))

  val errorResponse = ErrorResponse("Error", Some("General error"))
  val rawErrorResponse = JsObject(Seq(
    "error" -> JsString(errorResponse.error),
    "error_description" -> errorResponse.error_description.fold[JsValue](JsNull)(JsString)
  ))

  val tokenResponse = TokenResponse("token", 200, "tt")
  val rawTokenResponse = JsObject(Seq(
    "access_token" -> JsString(tokenResponse.access_token),
    "expires_in" -> JsNumber(tokenResponse.expires_in),
    "token_type" -> JsString(tokenResponse.token_type)
  ))

  type Result[T] = Either[ErrorResponse, T]

  "UmSentranaService ! SignOut" should {

    "sign user out" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.OK)))
      (umSentranaService ? SignOut("foo")).mapTo[Unit].futureValue
    }

    "fail cause of unexpected response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.BadRequest)))
      whenReady((umSentranaService ? SignOut("foo")).mapTo[Unit].failed)(_ shouldBe an[UnexpectedResponseException])
    }
  }

  "UmSentranaService ! SignIn" should {

    "return TokenResponse by login" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.OK,
        entity = httpEntity(rawTokenResponse)
      )))

      whenReady((umSentranaService ? SignIn(
        userParams = SampleUser.username,
        password = "pwd",
        isEmail = false
      )).mapTo[Result[TokenResponse]])(_ shouldBe tokenResponse.asRight)
    }

    "return TokenResponse by email" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.OK,
        entity = httpEntity(rawTokenResponse)
      )))

      whenReady((umSentranaService ? SignIn(
        userParams = SampleUser.email,
        password = "pwd",
        isEmail = true
      )).mapTo[Result[TokenResponse]])(_ shouldBe tokenResponse.asRight)
    }

    "return ErrorResponse cause of bad request code" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.BadRequest,
        entity = httpEntity(rawErrorResponse)
      )))

      whenReady((umSentranaService ? SignIn(
        userParams = SampleUser.username,
        password = "pwd",
        isEmail = false
      )).mapTo[Result[TokenResponse]])(_ shouldBe errorResponse.asLeft)
    }

    "fail cause of unexpected response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.Unauthorized,
        entity = httpEntity(rawErrorResponse)
      )))

      whenReady((umSentranaService ? SignIn(
        userParams = SampleUser.username,
        password = "pwd",
        isEmail = false
      )).mapTo[Result[TokenResponse]].failed)(_ shouldBe an[UnexpectedResponseException])
    }
  }

  "UmSentranaService ! SignUp" should {

    val signUpResponse = SignUpResponse(SampleUser.id, "Welcome to Sentrana!")
    val rawSignUpResponse = JsObject(Seq(
      "message" -> JsString(signUpResponse.message),
      "id" -> JsString(signUpResponse.id.toString)
    ))

    "return SignUpResponse" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(
          status = StatusCodes.OK,
          entity = httpEntity(rawSignUpResponse)
        )))

      whenReady((umSentranaService ? SignUp(
        username = SampleUser.username,
        email = SampleUser.email,
        password = "pwd",
        firstName = SampleUser.firstName,
        lastName = SampleUser.lastName,
        requireEmailConfirmation = true
      )).mapTo[Result[SignUpResponse]])(_ shouldBe signUpResponse.asRight)
    }

    "return ErrorResponse cause of bad request code" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(
        status = StatusCodes.BadRequest,
        entity = httpEntity(rawErrorResponse)
      )))

      whenReady((umSentranaService ? SignUp(
        username = SampleUser.username,
        email = SampleUser.email,
        password = "pwd",
        firstName = SampleUser.firstName,
        lastName = SampleUser.lastName,
        requireEmailConfirmation = true
      )).mapTo[Result[SignUpResponse]])(_ shouldBe errorResponse.asLeft)
    }

    "fail cause of unexpected response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.Forbidden,
        entity = httpEntity(rawErrorResponse)
      )))

      whenReady((umSentranaService ? SignUp(
        username = SampleUser.username,
        email = SampleUser.email,
        password = "pwd",
        firstName = SampleUser.firstName,
        lastName = SampleUser.lastName,
        requireEmailConfirmation = true
      )).mapTo[Result[SignUpResponse]].failed)(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "UmSentranaService ! InitiatePasswordReset" should {

    "initiate password reset procedure for user" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.OK)))
      (umSentranaService ? InitiatePasswordReset(SampleUser.email)).mapTo[Unit].futureValue
    }

    "fail cause of unexpected response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.NotFound,
        entity = httpEntity(rawErrorResponse)
      )))

      whenReady(
        (umSentranaService ? InitiatePasswordReset(SampleUser.email)).mapTo[Unit].failed
      )(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "UmSentranaService ! CompletePasswordReset" should {

    "complete password reset for user" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.OK)))

      whenReady(
        (umSentranaService ? CompletePasswordReset(SampleUser.email, "code", "newpwd")).mapTo[Result[Unit]]
      )(_ shouldBe ().asRight)
    }

    "return ErrorResponse cause of bad request code" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.BadRequest,
        entity = httpEntity(rawErrorResponse)
      )))

      whenReady(
        (umSentranaService ? CompletePasswordReset(SampleUser.email, "code", "newpwd")).mapTo[Result[Unit]]
      )(_ shouldBe errorResponse.asLeft)
    }

    "fail cause of unexpected response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.Forbidden,
        entity = httpEntity(rawErrorResponse)
      )))

      whenReady(
        (umSentranaService ? CompletePasswordReset(SampleUser.email, "code", "newpwd")).mapTo[Result[Unit]].failed
      )(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "UmSentranaService ! activateUser" should {

    "activate the user" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawUserResponse))))

      whenReady(
        (umSentranaService ? ActivateUser(UUID.randomUUID())).mapTo[Result[UserResponse]]
      )(_ shouldBe userResponse.asRight)
    }

    "user not found" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.BadRequest, entity = httpEntity(rawErrorResponse))))

      whenReady(
        (umSentranaService ? ActivateUser(UUID.randomUUID())).mapTo[Result[Unit]]
      )(_ shouldBe errorResponse.asLeft)
    }

  }

  "UmSentranaService ! deactivateUser" should {

    "deactivate the user" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(
          HttpResponse(
            status = StatusCodes.OK,
            entity = httpEntity(rawUserResponse)
          )
        ))

      whenReady(
        (umSentranaService ? DeactivateUser(UUID.randomUUID(), true)).mapTo[Result[UserResponse]]
      )(_ shouldBe userResponse.asRight)
    }

    "user not found" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(HttpResponse(
          status = StatusCodes.BadRequest,
          entity = httpEntity(rawErrorResponse)
        )))

      whenReady(
        (umSentranaService ? DeactivateUser(UUID.randomUUID(), true)).mapTo[Result[Unit]]
      )(_ shouldBe errorResponse.asLeft)
    }

  }

  "UmSentranaService ! UpdatePassword" should {

    "update password for user" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(
        HttpResponse(
          status = StatusCodes.OK,
          entity = httpEntity(JsObject(Seq.empty))
        )
      ))

      whenReady(
        (umSentranaService ? UpdatePassword("token", "pwd", "newpwd")).mapTo[Result[Unit]]
      )(_ shouldBe ().asRight)
    }

    "return ErrorResponse cause of bad request code" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.BadRequest,
        entity = httpEntity(rawErrorResponse)
      )))

      whenReady(
        (umSentranaService ? UpdatePassword("token", "pwd", "newpwd")).mapTo[Result[Unit]]
      )(_ shouldBe errorResponse.asLeft)
    }

    "fail cause of unexpected response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(
        status = StatusCodes.Forbidden,
        entity = httpEntity(rawErrorResponse)
      )))

      whenReady(
        (umSentranaService ? UpdatePassword("token", "pwd", "newpwd")).mapTo[Result[Unit]].failed
      )(_ shouldBe an[UnexpectedResponseException])
    }

  }

  val organizationResponse = OrganizationResponse("42", "deepcortex")
  val rawOrganizationResponse = JsObject(Seq(
    "id" -> JsString(organizationResponse.id),
    "name" -> JsString(organizationResponse.name)
  ))

  val zdtNow = ZonedDateTime.now
  val created = zdtNow.minusDays(2)
  val updated = zdtNow.minusHours(3)

  val userResponse = UserResponse(
    id = SampleUser.id,
    username = SampleUser.username,
    email = SampleUser.email,
    firstName = SampleUser.firstName,
    lastName = SampleUser.lastName,
    status = UserStatus.Active,
    fromRootOrg = false,
    created = created,
    updated = updated,
    permissions = Seq(),
    userGroupIds = Set()
  )

  val userResponseInactive = UserResponse(
    id = SampleUser.id,
    username = SampleUser.username,
    email = SampleUser.email,
    firstName = SampleUser.firstName,
    lastName = SampleUser.lastName,
    status = UserStatus.Inactive,
    fromRootOrg = false,
    created = created,
    updated = updated,
    permissions = Seq(),
    userGroupIds = Set()
  )

  val rawPermission = JsObject(Seq(
    "name" -> JsString("SUPER_USER")
  ))

  val rawUserResponse = JsObject(Seq(
    "id" -> Json.toJson(SampleUser.id),
    "username" -> JsString(SampleUser.username),
    "email" -> JsString(SampleUser.email),
    "firstName" -> JsString(SampleUser.firstName),
    "lastName" -> JsString(SampleUser.lastName),
    "status" -> JsString(UserStatus.Active.toString.toUpperCase),
    "fromRootOrg" -> JsBoolean(false),
    "created" -> Json.toJson(created),
    "updated" -> Json.toJson(updated),
    "permissions" -> JsArray(),
    "userGroupIds" -> JsArray(Seq())
  ))

  val rawUserResponseInactive = JsObject(Seq(
    "id" -> Json.toJson(SampleUser.id),
    "username" -> JsString(SampleUser.username),
    "email" -> JsString(SampleUser.email),
    "firstName" -> JsString(SampleUser.firstName),
    "lastName" -> JsString(SampleUser.lastName),
    "status" -> JsString(UserStatus.Inactive.toString.toUpperCase),
    "fromRootOrg" -> JsBoolean(false),
    "created" -> Json.toJson(created),
    "updated" -> Json.toJson(updated),
    "permissions" -> JsArray(),
    "userGroupIds" -> JsArray(Seq())
  ))

  val rawUserCreationResponse = JsObject(Seq(
    "id" -> Json.toJson(SampleUser.id),
    "message" -> Json.toJson("Success")
  ))

  "UmSentranaService ! DeleteUser" should {

    "return GenericResponse" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawGenericResponse))))

      whenReady(
        (umSentranaService ? DeleteUser(UUID.randomUUID())).mapTo[Result[Unit]]
      )(_ shouldBe ().asRight)
    }

    "return ErrorResponse cause of not found code" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.NotFound, entity = httpEntity(rawErrorResponse))))

      whenReady(
        (umSentranaService ? DeleteUser(UUID.randomUUID())).mapTo[Result[GenericResponse]]
      )(_ shouldBe errorResponse.asLeft)
    }

  }

  "UmSentranaService ! GetUser" should {

    "return UserResponse" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawUserResponse))))

      whenReady(
        (umSentranaService ? GetUser(SampleUser.id)).mapTo[Result[UserResponse]]
      )(_ shouldBe userResponse.asRight)
    }

    "return ErrorResponse cause of not found code" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.NotFound, entity = httpEntity(rawErrorResponse))))

      whenReady(
        (umSentranaService ? GetUser(SampleUser.id)).mapTo[Result[UserResponse]]
      )(_ shouldBe errorResponse.asLeft)
    }

    "fail cause of application access token update failure" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.Unauthorized, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.Unauthorized, entity = httpEntity(rawTokenResponse))))

      whenReady(
        (umSentranaService ? GetUser(SampleUser.id)).mapTo[Result[UserResponse]].failed
      )(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "UmSentranaService ! FindUsers" should {

    val userListResponse = ListResponse(Seq(userResponse, userResponse), 0, 2)
    val rawUserListResponse = JsObject(Seq(
      "data" -> JsArray(Seq(rawUserResponse, rawUserResponse)),
      "offset" -> JsNumber(0),
      "total" -> JsNumber(2)
    ))

    "return sequence of list responses" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawUserListResponse))))

      whenReady(umSentranaService ? FindUsers(
        None,
        None,
        Some(SampleUser.username),
        Some(SampleUser.email),
        None,
        Some("dc"),
        0,
        10
      ))(_ shouldBe userListResponse.asRight)
    }

    "fail cause of unexpected response" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.BadRequest, entity = httpEntity(rawErrorResponse))))

      whenReady((umSentranaService ? FindUsers(
        None,
        None,
        Some(SampleUser.username),
        Some(SampleUser.email),
        None,
        Some("dc"),
        0,
        10
      )).failed)(_ shouldBe an[UnexpectedResponseException])
    }

  }

  "UmSentranaService ! ValidateAccessToken" should {

    "return user response from server" in {
      when(cache.get(anyString)).thenReturn(None)
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawUserResponse))))
      when(cache.getOrLoad(anyString, any[String => Future[UserResponse]].apply)).thenReturn(future(userResponse))

      whenReady(
        (umSentranaService ? ValidateAccessToken(tokenResponse.access_token)).mapTo[Result[UserResponse]]
      )(_ shouldBe userResponse.asRight)
    }

    "return user response from cache" in {
      when(cache.get(anyString)).thenReturn(Some(future(userResponse)))

      whenReady(
        (umSentranaService ? ValidateAccessToken(tokenResponse.access_token)).mapTo[Result[UserResponse]]
      )(_ shouldBe userResponse.asRight)
    }

    "return ErrorResponse cause of bad request code" in {
      when(cache.get(anyString)).thenReturn(None)
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.BadRequest, entity = httpEntity(rawErrorResponse))))

      whenReady(
        (umSentranaService ? ValidateAccessToken(tokenResponse.access_token)).mapTo[Result[UserResponse]]
      )(_ shouldBe errorResponse.asLeft)
    }

    "fail cause of unexpected response" in {
      when(cache.get(anyString)).thenReturn(None)
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      )).thenReturn(future(HttpResponse(status = StatusCodes.Unauthorized, entity = httpEntity(rawErrorResponse))))

      whenReady(
        (umSentranaService ? ValidateAccessToken(tokenResponse.access_token)).mapTo[Result[UserResponse]].failed
      )(_ shouldBe an[UnexpectedResponseException])
    }

  }

  val userCreationResponse = SampleUser.id

  val message = randomString()

  val rawGenericResponse = Json.obj(
    "message" -> JsString(message),
    "id" -> JsString(SampleUser.id.toString)
  )

  "UmSentranaService ! CreateUser" should {
    "return UserResponse" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawUserResponse))))

      whenReady((umSentranaService ? CreateUser(
        username = SampleUser.username,
        email = SampleUser.email,
        password = "pwd",
        firstName = SampleUser.firstName,
        lastName = SampleUser.lastName,
        role = SampleUser.role,
        requireEmailConfirmation = true
      ))
        .mapTo[Result[UserResponse]]
      )(_ shouldBe userResponse.asRight)
    }

    "return ErrorResponse cause of bad request code" in {
      when(cache.get(anyString)).thenReturn(None)
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.BadRequest, entity = httpEntity(rawErrorResponse))))

      whenReady(
        (umSentranaService ? CreateUser(
          username = SampleUser.username,
          email = SampleUser.email,
          password = "pwd",
          firstName = SampleUser.firstName,
          lastName = SampleUser.lastName,
          role = SampleUser.role,
          requireEmailConfirmation = true
        )).mapTo[Result[ErrorResponse]]
      )(_ shouldBe errorResponse.asLeft)
    }

  }

  "UmSentranaService ! UpdateUser" should {
    "return UserResponse" in {
      when(httpMock.singleRequest(
        any[HttpRequest],
        any[HttpsConnectionContext],
        any[ConnectionPoolSettings],
        any[LoggingAdapter]
      ))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawTokenResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawOrganizationResponse))))
        .thenReturn(future(HttpResponse(status = StatusCodes.OK, entity = httpEntity(rawUserResponse))))

      whenReady(umSentranaService ? UpdateUser(
        userId = SampleUser.id,
        username = Some(SampleUser.username),
        email = Some(SampleUser.email),
        password = Some("pwd"),
        firstName = Some(SampleUser.firstName),
        lastName = Some(SampleUser.lastName),
        role = None
      ))(_ shouldBe userResponse.asRight)
    }

  }

}
