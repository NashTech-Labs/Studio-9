package com.sentrana.umserver.controllers

import com.sentrana.umserver.dtos.UpdateOrganizationRequest
import com.sentrana.umserver.entities.{PasswordReset, MongoFormats, UserEntity}
import com.sentrana.umserver.services._
import com.sentrana.umserver.shared.JsonFormatsShared
import com.sentrana.umserver.shared.dtos._
import com.sentrana.umserver.shared.dtos.enums.{FilterOperator, PasswordResetStatus, UserStatus}
import com.sentrana.umserver.utils.PasswordHash
import com.sentrana.umserver.{IntegrationTestUtils, JsonFormats, OneServerWithMongo, WithAdminUser}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.model.Filters.{equal => mequal}
import org.scalatest.OptionValues
import org.scalatestplus.play.PlaySpec
import play.api.cache.CacheApi
import play.api.http.MimeTypes
import play.api.libs.json._
import play.api.libs.ws.{WS, WSResponse}
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by Paul Lysak on 11.04.16.
  */
class UserControllerSpec extends PlaySpec with OneServerWithMongo with WithAdminUser with OptionValues {
  import IntegrationTestUtils.IT_PREFIX
  import com.sentrana.umserver.entities.MongoFormats.userEntityMongoFormat


  override def additionalConfig: Map[String, _] = super.additionalConfig ++ Map("reset.password.timeout" -> "5 m",
    "play.mailer.mock"->"true",
    "umserver.password.reset.duration" -> "5 m",
    "umserver.password.reset.update.duration" -> "1 m")

  private implicit lazy val userStatusReads = new JsonFormatsShared().userStatusReads
  private implicit lazy val filterOperatorReads = new JsonFormatsShared().filterOperatorReads

  private implicit val dataFilterInstanceReads = Json.reads[DataFilterInstance]
  import JsonFormats.orgStatusReads
  private implicit val orgReads = Json.reads[Organization]
  private implicit val userReads = Json.reads[User]

  private def usersBaseUrl(orgId: String) = s"$baseUrl/orgs/${orgId}/users"

  private val USER1_NAME = IT_PREFIX + "sampleUser1"
  private val USER1_NAME2 = IT_PREFIX + "sampleUpdatedUser1"
  private val USER1_EMAIL = IT_PREFIX + "sampleEmail@server.com"
  private val USER1_EMAIL2 = IT_PREFIX + "sampleEmail2@server.com"

  private val SU_USER1_NAME = IT_PREFIX + "signUpSampleUser1"
  private val SU_USER1_EMAIL = IT_PREFIX + "signUpSampleUser1@server.com"

  private val userCopyEmail = "userCopy@test.cl"
  private var userCopyOneId: String = _

  private lazy val orgOne = itUtils.createTestOrg("orgOne", rootOrg.id)
  private lazy val orgTwo = itUtils.createTestOrg("orgTwo", rootOrg.id)

  private lazy val sampleApp1 = itUtils.createTestApplicationInfo(
    "sampleApp",
    url = Option("http://sample.application.com"),
    emailConfirmationUrl = Option("http://sample.application.com")
  )

  private lazy val userWithDuplicateName = itUtils.createTestUser("userWithDuplicateName", orgId = orgOne.id, email = Option("uniqueEmail@some.test"))
  private lazy val userToChangeUserName = itUtils.createTestUser("userToChangeUserName", orgId = orgOne.id)

  private lazy val userQService = app.injector.instanceOf(classOf[UserQueryService])
  private lazy val userService = app.injector.instanceOf(classOf[UserService])
  private lazy val orgQService = app.injector.instanceOf(classOf[OrganizationService])
  private lazy val mongoDbService = app.injector.instanceOf(classOf[MongoDbService])
  private lazy val cache = app.injector.instanceOf(classOf[CacheApi])

  private var user1Id: String = _

  private lazy val filter1 = itUtils.createTestDataFilterInfo("someField")

  "UserController" must {
    "return 404 when trying to get non-existent user" in {
      rootOrg

      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + "/noSuchUser").withQueryString("access_token" -> adminToken).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(NOT_FOUND) }
    }

    "create new user" in {
      val resp = await(postData(getCreateUserRequestBody(USER1_NAME, USER1_EMAIL), usersBaseUrl(rootOrg.id)))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      user1Id = (resp.json \ "id").as[String]
      user1Id must not be empty
    }

    "not create user in regular org with root org group" in {
      val regularGroup = itUtils.createTestGroup("rg1", orgId = rootOrg.id + "_test")
      val resp = await(postData(getCreateUserRequestBody(USER1_NAME, USER1_EMAIL, s""""${regularGroup.id}""""), usersBaseUrl(rootOrg.id)))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
      (resp.json \ "message").as[String] mustBe (s"Root org user can't belong to child org ${regularGroup.organizationId} group ${regularGroup.id}")
    }

    "salt the password" in {
      val actualPassword = await(userQService.get(rootOrg.id, user1Id)).value.password
      PasswordHash.parse(actualPassword).checkPassword("knockKnock") must be (true)
    }

    "read freshly created user" in {
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + "/" + user1Id).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      (resp.json \ "id").as[String] mustBe(user1Id)
      (resp.json \ "username").as[String] mustBe(USER1_NAME)
      (resp.json \ "email").as[String] mustBe(USER1_EMAIL)
      (resp.json \ "firstName").as[String] mustBe("John")
      (resp.json \ "lastName").as[String] mustBe("Doe")
      (resp.json \ "status").as[String] mustBe("INACTIVE")
      (resp.json \ "organizationId").as[String] mustBe(rootOrg.id)
      (resp.json \ "created").as[String] must not be empty
      (resp.json \ "updated").as[String] must not be empty
      (resp.json \ "dataFilterInstances").as[Set[DataFilterInstance]] mustBe empty
    }

    "read freshly created user with Authorization header" in {
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + "/" + user1Id).withHeaders(("Authorization", "Bearer "+adminToken)).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
      (resp.json \ "id").as[String] mustBe (user1Id)
    }

    "deny access without access token" in {
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + "/" + user1Id).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (UNAUTHORIZED) }
    }

    "deny access with incorrect access token" in {
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + "/" + user1Id).withHeaders(("Authorization", "Bearer thisiswrongtoken")).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (UNAUTHORIZED) }
    }

    "update without specifying fields" in {
      val body = s"""
                    |{}
        """.stripMargin
      val resp = await(updateUser(usersBaseUrl(rootOrg.id) + "/" + user1Id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val actualUser = await(userQService.getMandatory(rootOrg.id, user1Id))
      actualUser.id mustBe(user1Id)
      actualUser.username mustBe(USER1_NAME)
      actualUser.email mustBe(USER1_EMAIL)
      actualUser.firstName mustBe("John")
      actualUser.lastName mustBe("Doe")
      actualUser.organizationId mustBe(rootOrg.id)
      actualUser.status mustBe(UserStatus.INACTIVE)
    }

    "update with specifying all fields" in {
      val body = s"""
                    |{"username": "$USER1_NAME2",
                    |"email": "$USER1_EMAIL2",
                    |"firstName": "Vasya",
                    |"lastName": "Pupkin",
                    |"password": "newPassword"
                    |}
        """.stripMargin
      val resp = await(updateUser(usersBaseUrl(rootOrg.id) + "/" + user1Id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val actualUser = await(userQService.getMandatory(rootOrg.id, user1Id))
      actualUser.id mustBe(user1Id)
      actualUser.username mustBe(USER1_NAME2)
      actualUser.email mustBe(USER1_EMAIL2)
      actualUser.firstName mustBe("Vasya")
      actualUser.lastName mustBe("Pupkin")
      actualUser.status mustBe(UserStatus.INACTIVE)
    }

    "fail to deactivate the user without USER_DEACTIVATE permission" in {
      val resp = await(postData(
        "{}",
        usersBaseUrl(rootOrg.id) + "/" + user1Id + "/deactivateuser",
        Map("access_token" -> userWithNoPermissionsToken)
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(FORBIDDEN) }
      val actualUser = await(userQService.getMandatory(rootOrg.id, user1Id))
      actualUser.id mustBe(user1Id)
      actualUser.status mustBe(UserStatus.INACTIVE)
    }

    "deactivate the user" in {
      val resp = await(postData(
        "{}",
        usersBaseUrl(rootOrg.id) + "/" + user1Id + "/deactivateuser"
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val actualUser = await(userQService.getMandatory(rootOrg.id, user1Id))
      actualUser.id mustBe(user1Id)
      actualUser.status mustBe(UserStatus.DEACTIVATED)
    }

    "fail to activate the user without USER_ACTIVATE permission" in {
      val resp = await(postData(
        "{}",
        usersBaseUrl(rootOrg.id) + "/" + user1Id + "/activateuser",
        Map("access_token" -> userWithNoPermissionsToken)
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(FORBIDDEN) }
      val actualUser = await(userQService.getMandatory(rootOrg.id, user1Id))
      actualUser.id mustBe(user1Id)
      actualUser.status mustBe(UserStatus.DEACTIVATED)
    }

    "activate the deactivated user with USER_ACTIVATE permission" in {
      val resp = await(postData(
        "{}",
        usersBaseUrl(rootOrg.id) + "/" + user1Id + "/activateuser",
        Map("access_token" -> userWithDeactivatePermissionToken)
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val actualUser = await(userQService.getMandatory(rootOrg.id, user1Id))
      actualUser.id mustBe(user1Id)
      actualUser.status mustBe(UserStatus.ACTIVE)
    }

    "deactivate the user with USER_DEACTIVATE permission" in {
      val resp = await(postData(
        "{}",
        usersBaseUrl(rootOrg.id) + "/" + user1Id + "/deactivateuser",
        Map("access_token" -> userWithDeactivatePermissionToken)
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val actualUser = await(userQService.getMandatory(rootOrg.id, user1Id))
      actualUser.id mustBe(user1Id)
      actualUser.status mustBe(UserStatus.DEACTIVATED)
    }

    "fail when tries deactivate that user which is not activated" in {
      val resp = await(postData(
        "{}",
        usersBaseUrl(rootOrg.id) + "/" + user1Id + "/deactivateuser"
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "activate the deactivated user" in {
      val resp = await(postData(
        "{}",
        usersBaseUrl(rootOrg.id) + "/" + user1Id + "/activateuser"
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val actualUser = await(userQService.getMandatory(rootOrg.id, user1Id))
      actualUser.id mustBe(user1Id)
      actualUser.status mustBe(UserStatus.ACTIVE)

      val tokenBaseUrl = s"$baseUrl/token"

      val tokenResponse = await(WS.url(tokenBaseUrl).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(actualUser.username),
          "password" -> Seq(actualUser.password))))
      withClue("Response body: " + resp.body) {
        tokenResponse.status mustBe (BAD_REQUEST)
      }
    }

    "fail when tries to activate an activated user" in {
      val resp = await(postData(
        "{}",
        usersBaseUrl(rootOrg.id) + "/" + user1Id + "/activateuser"
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "delete the user" in {
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + "/" + user1Id).withQueryString("access_token" -> adminToken).delete())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val actualUser = await(userQService.getMandatory(rootOrg.id, user1Id))
      actualUser.id mustBe(user1Id)
      actualUser.status mustBe(UserStatus.DELETED)
    }

    "read status of deleted user" in {
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + "/" + user1Id).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      (resp.json \ "status").as[String] mustBe("DELETED")
    }

    "create additional users" in {
      val createUsersResp = await(createUsers(5))
      createUsersResp.foreach(resp => (resp.json \ "id").as[String] must not be empty)
    }

    "find users with limit" in {
      val usersToReturnLimit = 5
      val resp = await(WS.url(usersBaseUrl(rootOrg.id)).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "limit" -> usersToReturnLimit.toString
        ).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      val userDtos = (resp.json \ "data").as[Seq[User]]
      userDtos.length mustBe usersToReturnLimit
    }

    "find users with offset" in {
      val usersOffset = 2
      val resp = await(WS.url(usersBaseUrl(rootOrg.id)).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "offset" -> usersOffset.toString
        ).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val usersCount = await(mongoDbService.count[UserEntity](notDeleted))
      val userDtos = (resp.json \ "data").as[Seq[User]]
      userDtos.length mustBe (usersCount - usersOffset)
    }

    "find users with limit and offset" in {
      val usersOffset = 2
      val usersLimit = 4
      val resp = await(WS.url(usersBaseUrl(rootOrg.id)).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "offset" -> usersOffset.toString,
          "limit" -> usersLimit.toString
        ).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val userDtos = (resp.json \ "data").as[Seq[User]]
      userDtos.length mustBe usersLimit
    }

    "find user by email" in {
      val email = USER1_EMAIL + "1"
      val resp = await(WS.url(usersBaseUrl(rootOrg.id)).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "email" -> email
        ).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val userDtos = (resp.json \ "data").as[Seq[User]]
      userDtos.length mustBe 1

      val u = userDtos.head
      u.email must be (email)
    }

    "find user by email prefix" in {
      val email = USER1_EMAIL + "1"
      val emailPrefix = email.take(5)
      val respPref = await(WS.url(usersBaseUrl(rootOrg.id)).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "email_prefix" -> emailPrefix
        ).get())

      withClue("Response body: "+respPref.body) { respPref.status mustBe(OK) }

      val userDtosPref = (respPref.json \ "data").as[Seq[User]]
      userDtosPref.length must be > (1)
      userDtosPref.forall(_.email.startsWith(emailPrefix)) must be (true)

      val resp = await(WS.url(usersBaseUrl(rootOrg.id)).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "email_prefix" -> email
        ).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val userDtos = (resp.json \ "data").as[Seq[User]]
      userDtos.length must be (1)
      userDtos.head.email must be (email)
    }

    "find users and sort by email" in {
      val resp = await(WS.url(usersBaseUrl(rootOrg.id)).
        withHeaders(("Content-Type", "application/json")).
        withQueryString("access_token" -> adminToken,
          "order" -> "-email"
        ).get())

      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val userDtos = (resp.json \ "data").as[Seq[User]]
      val u = userDtos.head
      u.email must be (USER1_EMAIL+5)
    }

    "not sign-up if organization doesn't allow it" in {
      val resp = await(postData(getUserSignUpRequest(USER1_NAME, USER1_EMAIL), usersBaseUrl(rootOrg.id) + "/signup"))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
      (resp.json \ "message").as[String] mustBe s"Self-service sign up is not allowed for organization ${rootOrg.name}"
    }

    "update self service sign-up property" in {
      val updatedOrg = await(orgQService.update(rootOrg.id, UpdateOrganizationRequest(name = None, desc = None, signUpEnabled = Option(true))))
      updatedOrg.signUpEnabled mustBe true
    }

    "sign-up with deleted user name" in {
      itUtils.createTestApplicationInfo("sampleApp2", url = Option("http://sample2.application.com"), emailConfirmationUrl = Option("http://sample.application.com"))
      val resp = await(postData(getUserSignUpRequest(USER1_NAME2, USER1_EMAIL2), usersBaseUrl(rootOrg.id) + "/signup"))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      (resp.json \ "id").as[String].length must be >(0)
    }

    "sign-up with duplicate user name" in {
      val resp = await(postData(getUserSignUpRequest(USER1_NAME2, USER1_EMAIL), usersBaseUrl(rootOrg.id) + "/signup"))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
      (resp.json \ "message").as[String] must include ("already exists")
    }

    "sign-up with new user" in {
      val resp = await(postData(getUserSignUpRequest(SU_USER1_NAME, SU_USER1_EMAIL), usersBaseUrl(rootOrg.id) + "/signup"))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      (resp.json \ "id").as[String].length must be >(0)
      val u = await(userQService.byUserName(rootOrg.id, SU_USER1_NAME)).headOption.value
      u.status must be (UserStatus.INACTIVE)
    }

    "re-send activation code" in {
      val resp = await(postData(
        s"""
          |{"email": "${SU_USER1_EMAIL}"}
        """.stripMargin, baseUrl + "/anonymous/email/activation"))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
    }

    "not activate user with incorrect code, json version" in {
      val u = await(userQService.byUserName(rootOrg.id, SU_USER1_NAME)).headOption.value
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + s"/${u.id}/activate").
        withHeaders(("Accept", "application/json")).
        withQueryString("activationCode" -> "someFakeCode").get())
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
      (resp.json \ "error").as[String] must not be (empty)

      val uAfter = await(userQService.byUserName(rootOrg.id, SU_USER1_NAME)).headOption.value
      uAfter.status must be (UserStatus.INACTIVE)
    }

    "activate user with correct code, json version" in {
      val u = await(userQService.byUserName(rootOrg.id, SU_USER1_NAME)).headOption.value
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + s"/${u.id}/activate").
        withHeaders(("Accept", "application/json")).
        withQueryString("activationCode" -> u.activationCode.value).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      (resp.json \ "message").as[String] must not be (empty)

      val uAfter = await(userQService.byUserName(rootOrg.id, SU_USER1_NAME)).headOption.value
      uAfter.status must be (UserStatus.ACTIVE)
      uAfter.activationCode must be (empty)
    }

    "not re-send activation code if user is already active" in {
      val resp = await(postData(
        s"""
          |{"email": "${SU_USER1_EMAIL}"}
        """.stripMargin, baseUrl + "/anonymous/email/activation"))
      withClue("Response body: "+resp.body) { resp.status mustBe(NOT_FOUND) }
    }

    "update filter instances" in {
      val body = s"""
                    |{
                    |"dataFilterInstances": [{
                    |  "dataFilterId": "${filter1.id}",
                    |  "operator": "EQ",
                    |  "values": ["firstVal"]
                    |}]
                    |}
        """.stripMargin
      val resp = await(updateUser(usersBaseUrl(rootOrg.id) + "/" + user1Id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val actualUser = await(userQService.getMandatory(rootOrg.id, user1Id))
      actualUser.dataFilterInstances must contain only (DataFilterInstance(filter1.id, FilterOperator.EQ, Set("firstVal")))
    }

    "get pre-processed filter instances" in {
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + "/" + user1Id + "/filterinstances").withQueryString("access_token" -> adminToken).get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      val instances = resp.json.as[Map[String, DataFilterInstance]]
      instances must be (Map(filter1.fieldName -> DataFilterInstance(filter1.id, FilterOperator.IN, Set("firstVal"))))
    }

    "not create user with an email which is already exists in the scope of current organization" in {
      val resp = await(postData(getCreateUserRequestBody(USER1_NAME, USER1_EMAIL2), usersBaseUrl(rootOrg.id)))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "not create user with an email which is already exists across organizations" in {
      val respCopyOne = await(postData(getCreateUserRequestBody(USER1_NAME, userCopyEmail), usersBaseUrl(orgOne.id)))
      withClue("Response body: "+respCopyOne.body) { respCopyOne.status mustBe(OK) }
      userCopyOneId = (respCopyOne.json \ "id").as[String]

      val respCopyTwo = await(postData(getCreateUserRequestBody(USER1_NAME, userCopyEmail), usersBaseUrl(orgTwo.id)))
      withClue("Response body: "+respCopyTwo.body) { respCopyTwo.status mustBe(BAD_REQUEST) }
    }

    "not create two non-deleted users with the same email in one org" in {
      val resp = await(WS.url(usersBaseUrl(orgOne.id) + "/" + userCopyOneId).withQueryString("access_token" -> adminToken).delete())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val respCopyOne = await(postData(getCreateUserRequestBody(USER1_NAME, userCopyEmail), usersBaseUrl(orgOne.id)))
      withClue("Response body: "+respCopyOne.body) { respCopyOne.status mustBe(OK) }
    }

    "not update user`s email to the one which is already exists in the current organization" in {
      val anotherUserOrgOne = itUtils.createTestUser("anotherUserOrgOne", orgId = orgOne.id)
      val body = s"""
                    |{"email": "$userCopyEmail"}
        """.stripMargin
      val resp = await(updateUser(usersBaseUrl(orgOne.id) + "/" + anotherUserOrgOne.id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "not update user`s email to the one which is already exists across organizations" in {
      val anotherUserOrgTwo = itUtils.createTestUser("anotherUserOrgTwo", orgId = orgTwo.id)
      val body = s"""
                    |{"email": "$userCopyEmail"}
        """.stripMargin
      val resp = await(updateUser(usersBaseUrl(orgTwo.id) + "/" + anotherUserOrgTwo.id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "not create two users with the same username in one org" in {
      val respCopyOne = await(postData(getCreateUserRequestBody(userWithDuplicateName.username, "another@unique.email"), usersBaseUrl(orgOne.id)))
      withClue("Response body: "+respCopyOne.body) { respCopyOne.status mustBe(BAD_REQUEST) }
    }

    "create user with the same name in one org if the previous user is deleted" in {
      val resp = await(WS.url(usersBaseUrl(orgOne.id) + "/" + userWithDuplicateName.id).withQueryString("access_token" -> adminToken).delete())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val respCopyOne = await(postData(getCreateUserRequestBody(userWithDuplicateName.username, "anotherWithDeletedInTheSameOrg@unique.email"), usersBaseUrl(orgOne.id)))
      withClue("Response body: "+respCopyOne.body) { respCopyOne.status mustBe(OK) }
    }

    "create user with the same name in a different organization" in {
      val respCopyOne = await(postData(getCreateUserRequestBody(userWithDuplicateName.username, "another@unique.email"), usersBaseUrl(orgTwo.id)))
      withClue("Response body: "+respCopyOne.body) { respCopyOne.status mustBe(OK) }
    }

    "not update username to the one which is already exists in current org" in {
      val body = s"""
                    |{"username": "${userWithDuplicateName.username}"}
        """.stripMargin
      val resp = await(updateUser(usersBaseUrl(orgOne.id) + "/" + userToChangeUserName.id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "update username to the one that exists in a different organization" in {
      val anotherUserToChangeUserNameOrgTwo = itUtils.createTestUser("anotherUserToChangeUserNameOrgTwo", orgId = orgTwo.id)

      val body = s"""
                    |{"username": "${userToChangeUserName.username}"}
        """.stripMargin
      val resp = await(updateUser(usersBaseUrl(orgTwo.id) + "/" + anotherUserToChangeUserNameOrgTwo.id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
    }

    "update username to the one which is already exists in the current organization if previous user is deleted" in {
      val respToDelete = await(WS.url(usersBaseUrl(orgOne.id) + "/" + userToChangeUserName.id).withQueryString("access_token" -> adminToken).delete())
      withClue("Response body: "+respToDelete.body) { respToDelete.status mustBe(OK) }

      val body = s"""
                    |{"username": "${userToChangeUserName.username}"}
        """.stripMargin
      val resp = await(updateUser(usersBaseUrl(orgOne.id) + "/" + userToChangeUserName.id, body, adminToken))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
    }

    "not update users password" in {
      val body =
        s"""
           |{
           | "oldPassword":"somePas",
           | "newPassword":"empty"
           |}
        """.stripMargin

      val userToChangePassword = itUtils.createTestUser("userToChangePasswordNegativeCase", "testpassword", orgOne.id)
      val userToChangePasswordToken = authService.issueToken(userToChangePassword)

      val resp = await(postData(body, s"$baseUrl/anonymous/password", Map("access_token" -> userToChangePasswordToken._1)))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
    }

    "return UNAUTHORIZED for either invalid or expired token" in {
      val body =
        s"""
           |{
           | "oldPassword":"empty",
           | "newPassword":"empty"
           |}
        """.stripMargin

      val resp = await(postData(body, s"$baseUrl/me/password", Map("access_token" -> "fakeToken")))
      withClue("Response body: "+resp.body) { resp.status mustBe(UNAUTHORIZED) }
    }

    "update users password" in {
      import MongoFormats.userPasswordHistoryMongoFormat

      val oldPassword = "oldPassword"
      val newPassword = "newPassword1"
      val body =
        s"""
          |{
          | "oldPassword":"${oldPassword}",
          | "newPassword":"${newPassword}"
          |}
        """.stripMargin

      val userToChangePassword = itUtils.createTestUser("userToChangePassword", oldPassword, orgOne.id)
      val userToChangePasswordToken = authService.issueToken(userToChangePassword)

      val resp = await(postData(body, s"$baseUrl/me/password", Map("access_token" -> userToChangePasswordToken._1)))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val userWithUpdatePassword = await(userQService.getMandatory(orgOne.id, userToChangePassword.id))
      PasswordHash.parse(userWithUpdatePassword.password).checkPassword(oldPassword) mustBe false
      PasswordHash.parse(userWithUpdatePassword.password).checkPassword(newPassword) mustBe true

      val userPasswordHistory = await(mongoDbService.find[UserPasswordHistory](mequal("userId", userWithUpdatePassword.id)).toFuture())
      userPasswordHistory.size mustBe 1
    }

    "send forgot password email and resetPassword old password" in {
      import com.sentrana.umserver.entities.MongoFormats.passwordResetMongoFormat
      val forgotPasswordUser = itUtils.createTestUser("forgotPasswordUser", "forgotPasswordUser", orgOne.id)
      val resp = await(postData(url = s"$baseUrl/anonymous/password/reset", params = Map.empty,
        body =
          s"""
            |{"email": "${forgotPasswordUser.email}"}
          """.stripMargin))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val passwordReset: PasswordReset =
        await(mongoDbService.find[PasswordReset](Document(s"""{"email":"${forgotPasswordUser.email}"}""")).toFuture()).headOption.value
      passwordReset.status mustBe PasswordResetStatus.ACTIVE
      passwordReset.email mustBe forgotPasswordUser.email
      passwordReset.secretCode must not be empty

      val newPassword = "ValidPwd123"
      val body =
        s"""
          |{
          |"email":"${passwordReset.email}",
          |"secretCode":"${passwordReset.secretCode}",
          |"newPassword":"${newPassword}"
          |}
        """.stripMargin

      val updatePasswordResp = await(postData(body = body, url = s"$baseUrl/anonymous/password", params = Map()))
      withClue("Response body: "+ updatePasswordResp.body) { updatePasswordResp.status mustBe(OK) }

      val passwordResets = await(mongoDbService.find[PasswordReset](Document(s"""{"email":"${forgotPasswordUser.email}"}""")).toFuture())
      passwordResets.exists(_.status == PasswordResetStatus.USED) mustBe true
    }

    "not send forgot password to non ACTIVE user" in {
      import com.sentrana.umserver.entities.MongoFormats.passwordResetMongoFormat
      UserStatus.values().collect {
        case userStatus if(userStatus != UserStatus.ACTIVE) =>
          val forgotPasswordNonActiveUser = itUtils.createTestUser("forgotPasswordNonActiveUser" + userStatus.name(), "forgotPasswordNonActiveUser", orgOne.id)

          await(userService.updateUserStatus(orgOne.id, forgotPasswordNonActiveUser.id, userStatus))
          val resp = await(postData(url = s"$baseUrl/anonymous/password/reset",
            params = Map("email" -> forgotPasswordNonActiveUser.email),
            body =
              s"""
                |{"email": "${forgotPasswordNonActiveUser.email}"}
              """.stripMargin
          ))
          withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
          await(mongoDbService.findSingle[PasswordReset](Document(s"""{"email":"${forgotPasswordNonActiveUser.email}"}"""))) must be (empty)
      }
      ()
    }

    "allow only 1 resetPassword request per user in 5 minutes" in {
      import com.sentrana.umserver.entities.MongoFormats.passwordResetMongoFormat
      val userToChangeForgottenPasswordMultipleTimes = itUtils.createTestUser("userToChangeForgottenPasswordMultipleTimes")
      val anotherUserToChangeForgottenPassword = itUtils.createTestUser("anotherUserToChangeForgottenPassword")

      val resp = await(postData(url = s"$baseUrl/anonymous/password/reset",
        params = Map.empty,
        body =
          s"""
            |{"email": "${userToChangeForgottenPasswordMultipleTimes.email}"}
          """.stripMargin))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val secondResp = await(postData(url = s"$baseUrl/anonymous/password/reset", params = Map.empty,
        body =
          s"""
            |{"email": "${userToChangeForgottenPasswordMultipleTimes.email}"}
          """.stripMargin
      ))
      withClue("Response body: "+secondResp.body) { secondResp.status mustBe(OK) }

      val anotehrUserResetPasswordResp = await(postData(url = s"$baseUrl/anonymous/password/reset", params = Map.empty,
        body =
          s"""
            |{"email": "${anotherUserToChangeForgottenPassword.email}"}
          """.stripMargin
      ))
      withClue("Response body: " + anotehrUserResetPasswordResp.body) { anotehrUserResetPasswordResp.status mustBe(OK) }

      val passwordReset: PasswordReset=
        await(mongoDbService.find[PasswordReset](Document(s"""{"email":"${userToChangeForgottenPasswordMultipleTimes.email}"}""")).toFuture()).headOption.value
      passwordReset.status mustBe PasswordResetStatus.ACTIVE

      await(mongoDbService.update[PasswordReset](passwordReset.copy(created = passwordReset.created.minusSeconds(360)), OrgScopeRoot))

      val thirdResp = await(postData(url = s"$baseUrl/anonymous/password/reset", params = Map.empty,
        body =
          s"""
            |{"email": "${userToChangeForgottenPasswordMultipleTimes.email}"}
          """.stripMargin
      ))
      withClue("Response body: " + thirdResp.body) { thirdResp.status mustBe(OK) }
    }

    "not allow to reset forgotten password with wrong secretCode frequently" in {
      import com.sentrana.umserver.entities.MongoFormats.passwordResetMongoFormat
      val frequentlyForgotPasswordUser = itUtils.createTestUser("frequentlyForgotPasswordUser")
      val resp = await(postData(url = s"$baseUrl/anonymous/password/reset", params = Map.empty,
         body =
          s"""
            |{"email": "${frequentlyForgotPasswordUser.email}"}
          """.stripMargin
      ))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val passwordReset: PasswordReset =
        await(mongoDbService.findSingle[PasswordReset](Document(s"""{"email":"${frequentlyForgotPasswordUser.email}"}"""))).value
      passwordReset.status mustBe PasswordResetStatus.ACTIVE

      val newPassword = "ValidPwd123"
      val bodyWithIncorrectSecretCode =
        s"""
           |{
           |"email":"${passwordReset.email}",
           |"secretCode":"${passwordReset.secretCode + "fake"}",
           |"newPassword":"${newPassword}"
           |}
        """.stripMargin

      val updatePasswordResp = await(postData(body = bodyWithIncorrectSecretCode, url = s"$baseUrl/anonymous/password", params = Map()))
      withClue("Response body: "+ updatePasswordResp.body) { updatePasswordResp.status mustBe(UNAUTHORIZED) }

      val bodyWithCorrectSecretCode =
        s"""
           |{
           |"email":"${passwordReset.email}",
           |"secretCode":"${passwordReset.secretCode}",
           |"newPassword":"${newPassword}"
           |}
        """.stripMargin

      val updatePasswordSecondTimeResp = await(postData(body = bodyWithCorrectSecretCode, url = s"$baseUrl/anonymous/password", params = Map()))
      withClue("Response body: "+ updatePasswordSecondTimeResp.body) { updatePasswordSecondTimeResp.status mustBe(TOO_MANY_REQUEST) }

      cache.remove(UserService.passwordUpdateAttemptKey(passwordReset.email))
      val updatePasswordThirdTimeResp = await(postData(body = bodyWithCorrectSecretCode, url = s"$baseUrl/anonymous/password", params = Map()))
      withClue("Response body: "+ updatePasswordThirdTimeResp.body) { updatePasswordThirdTimeResp.status mustBe(OK) }

      val passwordResets = await(mongoDbService.find[PasswordReset](Document(s"""{"email":"${frequentlyForgotPasswordUser.email}"}""")).toFuture())
      passwordResets.exists(_.status == PasswordResetStatus.USED) mustBe true
    }
  }

  private def postData(body: String = "{}", url: String,  params: Map[String, String] = Map("access_token" -> adminToken)): Future[WSResponse] = {
    WS.url(url).withHeaders(("Content-Type", "application/json")).withQueryString(params.toSeq:_ *).post(
      body
    )
  }

  private def updateUser(url: String, body: String, token: String): Future[WSResponse] = {
    WS.url(url).withHeaders(("Content-Type", "application/json")).
      withQueryString("access_token" -> token).put(body)
  }

  private def getCreateUserRequestBody(userName: String, email: String, groupId: String = ""): String = {
    s"""
       |{"username": "$userName",
       |"email": "$email",
       |"firstName": "John",
       |"lastName": "Doe",
       |"password": "knockKnock",
       |"groupIds": [${groupId}],
       |"dataFilterInstances":[]
       |}
        """.stripMargin
  }

  private def getUserSignUpRequest(userName: String, email: String): String = {
    s"""
       |{"username": "$userName",
       |"email": "$email",
       |"firstName": "John",
       |"lastName": "Doe",
       |"password": "knockKnock"
       |}
        """.stripMargin
  }

  private def createUsers(amount: Int): Future[Seq[WSResponse]] = {
    val usersWsResponses = (1 to amount).map { i =>
      postData(getCreateUserRequestBody(USER1_NAME + i, USER1_EMAIL + i), usersBaseUrl(rootOrg.id))
    }
    Future.sequence(usersWsResponses)
  }

  import org.mongodb.scala.model.Filters._

  private val notDeleted = notEqual("status", UserStatus.DELETED.toString)
}
