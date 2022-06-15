package baile.routes.usermanagement.util

import java.time.ZonedDateTime
import java.util.UUID

import baile.routes.contract.usermanagement.CreateUserRequest
import baile.domain.usermanagement.{ AccessToken, _ }
import play.api.libs.json.{ JsNumber, JsObject, JsString, Json }
import baile.services.usermanagement.util.TestData._
import play.api.libs.json._

object TestData {

  val SignInWithUserNameParametersRequestJson: String =
    """{
      |"username":"username",
      |"password":"password"
      |}""".stripMargin

  val SignInWithEmailParametersRequestJson: String =
    """{
      |"email":"email",
      |"password":"password"
      |}""".stripMargin

  val SignInWithPasswordOnlyRequestJson: String =
    """{
      |"password":"password"
      |}""".stripMargin

  val SignUpRequestJson: String =
    """{
      |"username":"username",
      |"email":"email",
      |"password":"password",
      |"firstName":"firstName",
      |"lastName":"lastName",
      |"requireEmailConfirmation":false
      |}""".stripMargin

  val AccessTokenSample = AccessToken("token", 1, "tokenType")

  val SignInResponse: JsObject = Json.obj(
    "access_token" -> JsString("token"),
    "expires_in" -> JsNumber(1),
    "token_type" -> JsString("tokenType")
  )

  val ZdtNow = ZonedDateTime.now

  val UserResponse = Json.obj(
    "id" -> JsString("e4575008-a1b0-4e22-9103-633bd1f1b437"),
    "username" -> JsString("john.doe"),
    "email" -> JsString("jd@example.com"),
    "firstName" -> JsString("John"),
    "lastName" -> JsString("Doe"),
    "status" -> JsString("ACTIVE"),
    "created" -> JsString(SampleUser.created.toString),
    "updated" -> JsString(SampleUser.updated.toString),
    "role" -> JsString("USER")
  )

  val AdminResponse: JsObject = Json.obj(
    "id" -> JsString("e4475008-a1b0-4e22-9103-633bd1f1b437"),
    "username" -> JsString("username"),
    "email" -> JsString("email"),
    "firstName" -> JsString("firstName"),
    "lastName" -> JsString("lastName"),
    "status" -> JsString("ACTIVE"),
    "created" -> JsString(SampleUser.created.toString),
    "updated" -> JsString(SampleUser.updated.toString),
    "role" -> JsString("ADMIN")
  )

  val ListUserResponse: JsObject = Json.obj(
    "data" -> Seq(Json.obj(
      "id" -> JsString("e4575008-a1b0-4e22-9103-633bd1f1b437"),
      "username" -> JsString("john.doe"),
      "email" -> JsString("jd@example.com"),
      "firstName" -> JsString("John"),
      "lastName" -> JsString("Doe"),
      "status" -> JsString("ACTIVE"),
      "created" -> JsString(SampleUser.created.toString),
      "updated" -> JsString(SampleUser.updated.toString),
      "role" -> JsString("USER")
    )),
    "count" -> 1
  )

  val Created: ZonedDateTime = ZdtNow.minusDays(2)
  val Updated: ZonedDateTime = ZdtNow.minusHours(3)

  val Admin: RegularUser = RegularUser(
    UUID.fromString("e4475008-a1b0-4e22-9103-633bd1f1b437"),
    "username",
    "email",
    "firstName",
    "lastName",
    UserStatus.Active,
    SampleUser.created,
    SampleUser.updated,
    Seq(Permission.SuperUser),
    Role.Admin
  )

  val UserCreateResponseForCreateUser = "e4475008-a1b0-4e22-9103-633bd1f1b437"

  val UserCreateResponseJson: JsObject = Json.obj("id" -> "e4475008-a1b0-4e22-9103-633bd1f1b437")

  val CreateUserRequestByAdmin: CreateUserRequest = CreateUserRequest(
    "user-name",
    "email",
    "password",
    "fName",
    "lName",
    Role.Admin
  )

  val UpdateUserRequestJson: JsObject = Json.obj(
    "username" -> JsString(CreateUserRequestByAdmin.username),
    "email" -> JsString(CreateUserRequestByAdmin.email),
    "password" -> JsString(CreateUserRequestByAdmin.password),
    "firstName" -> JsString(CreateUserRequestByAdmin.firstName),
    "lastName" -> JsString(CreateUserRequestByAdmin.lastName),
    "role" -> JsString("ADMIN")
  )

  val IdResponse: JsObject = Json.obj(
    "id" -> JsString(Admin.id.toString)
  )

  val UpdateUserRequest: String =
    s"""{
       |"username": "${ CreateUserRequestByAdmin.username }",
       |"email": "${ CreateUserRequestByAdmin.email }",
       |"password": "${ CreateUserRequestByAdmin.password }",
       |"firstName": "${ CreateUserRequestByAdmin.firstName }",
       |"lastName": "${ CreateUserRequestByAdmin.lastName }"
       |}""".stripMargin

  val CreateUserRequestJson: String =
    s"""{
       |"username" : "${ CreateUserRequestByAdmin.username }",
       |"email" : "${ CreateUserRequestByAdmin.email }",
       |"password": "${ CreateUserRequestByAdmin.password }",
       |"firstName": "${ CreateUserRequestByAdmin.firstName }",
       |"lastName": "${ CreateUserRequestByAdmin.lastName }",
       |"role": "ADMIN"
       |}""".stripMargin

  val InCorrectCreateUserRequestJson: String =
    s"""{"username" : "${ CreateUserRequestByAdmin.username }"}"""

  val Id = "e4475008-a1b0-4e22-9103-633bd1f1b437"

  val DeletionResponse: UUID =  UUID.fromString("e4475008-a1b0-4e22-9103-633bd1f1b437")

  val ResetPasswordCompleteRequestSampleJson: String =
    """{
      |"email":"email",
      |"secretCode":"secretCode",
      |"newPassword":"newPassword"
      |}""".stripMargin

  val ChangePasswordRequestJson: String =
    """{
      |"oldPassword":"oldPassword",
      |"newPassword":"newPassword"
      |}""".stripMargin

  val ResetPasswordRequestJson: String =
    """{
      |"email":"email"
      |}""".stripMargin

  val RemindUsernameRequestJson: String =
    """{
      |"email":"email"
      |}""".stripMargin

}
