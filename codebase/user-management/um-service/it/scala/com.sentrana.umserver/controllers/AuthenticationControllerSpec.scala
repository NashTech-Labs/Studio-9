package com.sentrana.umserver.controllers

import com.sentrana.umserver.services.{AuthenticationService, ApplicationInfoService, UserService, OrganizationService}
import com.sentrana.umserver.shared.dtos.enums.OrganizationStatus
import com.sentrana.umserver.{IntegrationTestUtils, OneServerWithMongo}
import com.sentrana.umserver.dtos.CreateUserRequest
import com.sentrana.umserver.entities.UserEntity
import com.sentrana.umserver.entities.ApplicationInfoEntity
import com.sentrana.umserver.shared.dtos.{ApplicationInfo, User}
import com.sentrana.umserver.shared.dtos.enums.UserStatus
import com.sentrana.umserver.{IntegrationTestUtils, OneServerWithMongo, entities}
import org.apache.commons.lang3.StringUtils
import org.jboss.netty.util.internal.StringUtil
import org.scalatestplus.play.PlaySpec
import play.api.libs.ws.WS
import play.api.test.Helpers._

/**
  * Created by Paul Lysak on 13.04.16.
  */
class AuthenticationControllerSpec extends PlaySpec with OneServerWithMongo {
  private lazy val TOKEN_BASE_URL = s"$baseUrl/token"
  private val IT_PREFIX = "integrationTest_"

  private val USER1_PASSWORD = "myPassword"

  private val APP_INFO1_NAME = IT_PREFIX + "applicationInfo1"

  private lazy val user1 = itUtils.createTestUser(IT_PREFIX + "sampleUser1", USER1_PASSWORD)
  private lazy val user2 = itUtils.createTestUser(IT_PREFIX + "sampleUser2", USER1_PASSWORD)
  private lazy val applicationInfo1 = itUtils.createTestApplicationInfo(APP_INFO1_NAME, None, None)
  private var user1Token: String = _
  private var app1Token: String = _

  private lazy val rootOrg = itUtils.createRootOrg()
  private lazy val org1 = itUtils.createTestOrg("org2", rootOrg.id)
  private lazy val org2 = itUtils.createTestOrg("org1", rootOrg.id)
  private lazy val org1user1 = itUtils.createTestUser("org1user1", orgId = org1.id)
  private lazy val org2user1 = itUtils.createTestUser("org2user1", orgId = org2.id)
  private lazy val org1dupUser = itUtils.createTestUser("dupUser", password = "org1", orgId = org1.id, email = Option("dupUser1@some.email"))
  private lazy val org2dupUser = itUtils.createTestUser("dupUser", password = "org2", orgId = org2.id, email = Option("dupUser2@some.email"))

  private lazy val itUtils = new IntegrationTestUtils()
  private lazy val userService = app.injector.instanceOf(classOf[UserService])
  private lazy val orgService = app.injector.instanceOf(classOf[OrganizationService])
  private lazy val authenticationService = app.injector.instanceOf(classOf[AuthenticationService])

  "AuthenticationController for user" must {
    "issue token to user with correct credentials" in {
      rootOrg

      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(user1.username),
          "password" -> Seq(USER1_PASSWORD))))
      withClue("Response body: " + resp.body) {
        resp.status mustBe (OK)
      }

      user1Token = (resp.json \ "access_token").as[String]
      user1Token must not be empty
      user1Token must not startWith (AuthenticationService.ClientTokenPrefix)
      (resp.json \ "token_type").as[String] must be("bearer")
      (resp.json \ "expires_in").as[Int] must be > 0
      resp.cookies must be (empty)
    }

    "issue token to user by email" in {
      rootOrg

      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "email" -> Seq(user1.email),
          "password" -> Seq(USER1_PASSWORD))))
      withClue("Response body: " + resp.body) {
        resp.status mustBe (OK)
      }

      user1Token = (resp.json \ "access_token").as[String]
      user1Token must not be empty
      user1Token must not startWith (AuthenticationService.ClientTokenPrefix)
      (resp.json \ "token_type").as[String] must be("bearer")
      (resp.json \ "expires_in").as[Int] must be > 0
      resp.cookies must be (empty)
    }


    "set token cookie to user with correct credentials" in {
      rootOrg

      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(user1.username),
          "password" -> Seq(USER1_PASSWORD),
          "set_cookie" -> Seq("true")
        )))
      withClue("Response body: " + resp.body) {
        resp.status mustBe (OK)
      }

      user1Token = (resp.json \ "access_token").as[String]
      user1Token must not be empty
      user1Token must not startWith (AuthenticationService.ClientTokenPrefix)
      (resp.json \ "token_type").as[String] must be("bearer")
      (resp.json \ "expires_in").as[Int] must be > 0
      resp.cookies.flatMap(c => c.name.zip(c.value).toSeq) must be (Seq("access_token" -> user1Token))
    }

    "issue token to application with correct client_id client_secret" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("client_credentials"),
          "client_id" -> Seq(applicationInfo1.id),
          "client_secret" -> Seq(applicationInfo1.clientSecret))))
      withClue("Response body: " + resp.body) {
        resp.status mustBe (OK)
      }

      app1Token = (resp.json \ "access_token").as[String]
      app1Token must not be empty
      app1Token must startWith(AuthenticationService.ClientTokenPrefix)
      (resp.json \ "token_type").as[String] must be("bearer")
      (resp.json \ "expires_in").as[Int] must be > 0
    }

    "not issue token if neither username, nor email is specified" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "password" -> Seq("wrongPassword")
        )))
      withClue("Response body: " + resp.body) { resp.status mustBe (BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_request"
      (resp.json \ "error_description").as[String] mustBe "Neither username, nor email is specified"
    }

    "not issue token if password is not specified" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(user1.username)
        )))
      withClue("Response body: " + resp.body) { resp.status mustBe (BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_request"
      (resp.json \ "error_description").as[String] mustBe "Password not specified"
    }

    "not issue token if no grant_type is specified" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("username" -> Seq(user1.username),
          "password" -> Seq("wrongPassword")
        )))
      withClue("Response body: " + resp.body) { resp.status mustBe (BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_request"
      (resp.json \ "error_description").as[String] mustBe "grant_type not specified"
    }

    "not issue token to user with incorrect password" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(user1.username),
          "password" -> Seq("wrongPassword")
        )))
      withClue("Response body: " + resp.body) { resp.status mustBe (BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_grant"
      (resp.json \ "error_description").as[String] mustBe "Invalid credentials"
    }

    "get user by valid token" in {
      val resp = await(WS.url(TOKEN_BASE_URL + "/"+user1Token+"/user").get())
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      (resp.json \ "id").as[String] must be (user1.id)
      (resp.json \ "username").as[String] must be (user1.username)
      (resp.json \ "firstName").as[String] must be (user1.firstName)
      (resp.json \ "lastName").as[String] must be (user1.lastName)
      (resp.json \ "email").as[String] must be (user1.email)
      (resp.json \ "status").as[String] must be (user1.status.toString)
    }

    "don't get user by invalid token" in {
      val resp = await(WS.url(TOKEN_BASE_URL + "/some1fake2token/user").get())
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_grant"
      (resp.json \ "error_description").as[String] mustBe "No such token"
    }

    "issue different user token on second request" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(user1.username),
          "password" -> Seq(USER1_PASSWORD)
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val user1Token2 = (resp.json \ "access_token").as[String]
      user1Token2 must not be (user1Token)
      user1Token2 must not startWith (AuthenticationService.ClientTokenPrefix)
      user1Token = user1Token2
    }

    "not issue token to inactive user" in {
      await(userService.updateUserStatus(rootOrg.id, user2.id, UserStatus.INACTIVE))

      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(user2.username),
          "password" -> Seq(USER1_PASSWORD)
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_grant"
      (resp.json \ "error_description").as[String] mustBe "Invalid credentials"
    }

    "invalidate user token" in {
      val getUserByValidTokenResp = await(WS.url(TOKEN_BASE_URL + "/"+user1Token+"/user").get())
      withClue("Response body: "+getUserByValidTokenResp.body) { getUserByValidTokenResp.status mustBe(OK) }

      val invalidateUserTokenResp = await(WS.url(TOKEN_BASE_URL + s"/${user1Token}").delete())
      withClue("Response body: " + invalidateUserTokenResp.body) {
        invalidateUserTokenResp.status mustBe (OK)
      }

      val getUserByInvalidatedTokenResp = await(WS.url(TOKEN_BASE_URL + "/"+user1Token+"/user").get())
      withClue("Response body: "+getUserByInvalidatedTokenResp.body) { getUserByInvalidatedTokenResp.status mustBe(BAD_REQUEST) }
      (getUserByInvalidatedTokenResp.json \ "error").as[String] mustBe "invalid_grant"
      (getUserByInvalidatedTokenResp.json \ "error_description").as[String] mustBe "No such token"
    }
  }

  "Authentication controller for apps" must {
    "not issue token to application with incorrect client_secret" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("client_credentials"),
          "client_id" -> Seq(applicationInfo1.id),
          "client_secret" -> Seq(applicationInfo1.clientSecret + "_fake")
        )))
      withClue("Response body: " + resp.body) { resp.status mustBe (BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_grant"
      (resp.json \ "error_description").as[String] mustBe "Invalid credentials"
    }

    "not issue token to application with incorrect client_id" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("client_credentials"),
          "client_id" -> Seq(applicationInfo1.id + "_fake"),
          "client_secret" -> Seq(applicationInfo1.clientSecret)
        )))
      withClue("Response body: " + resp.body) { resp.status mustBe (BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_grant"
      (resp.json \ "error_description").as[String] mustBe "Invalid credentials"
    }

    "not issue token to application without client_id and client_secret" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("client_credentials"))))
      withClue("Response body: " + resp.body) { resp.status mustBe (BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_request"
      (resp.json \ "error_description").as[String] mustBe "Either client_id or client_secret not specified"
    }

    "not issue token to incorrect grant_type" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("grant_type"),
          "client_id" -> Seq(applicationInfo1.id + "_fake"),
          "client_secret" -> Seq(applicationInfo1.clientSecret),
          "username" -> Seq(user1.username),
          "password" -> Seq("wrongPassword")
        )))
      withClue("Response body: " + resp.body) { resp.status mustBe (BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "unsupported_grant_type"
    }

    "get Dummy user by valid token" in {
      val resp = await(WS.url(TOKEN_BASE_URL + "/" + app1Token + "/user").get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }

      (resp.json \ "id").as[String] must be(ApplicationInfo.DUMMY_ID)
      (resp.json \ "username").as[String] must be(applicationInfo1.id)
      (resp.json \ "firstName").as[String] must be(applicationInfo1.name)
      (resp.json \ "lastName").as[String] must be(StringUtils.EMPTY)
      (resp.json \ "email").as[String] must be(StringUtils.EMPTY)
      (resp.json \ "status").as[String] must be(UserStatus.ACTIVE.name)
    }


    "issue different application token on second request" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("client_credentials"),
          "client_id" -> Seq(applicationInfo1.id),
          "client_secret" -> Seq(applicationInfo1.clientSecret))))
      withClue("Response body: " + resp.body) {
        resp.status mustBe (OK)
      }

      val appToken2 = (resp.json \ "access_token").as[String]
      appToken2 must not be (app1Token)
      appToken2 must startWith(AuthenticationService.ClientTokenPrefix)
      app1Token = appToken2
    }

    "invalidate applicationToken" in {
      val getDummyUserResp = await(WS.url(TOKEN_BASE_URL + "/" + app1Token + "/user").get())
      withClue("Response body: " + getDummyUserResp.body) {
        getDummyUserResp.status mustBe (OK)
      }

      val invalidateAppTokenResp = await(WS.url(TOKEN_BASE_URL + s"/${app1Token}").delete())
      withClue("Response body: " + invalidateAppTokenResp.body) {
        invalidateAppTokenResp.status mustBe (OK)
      }

      val getDummyUserByInvalidatedTokenResp = await(WS.url(TOKEN_BASE_URL + "/" + app1Token + "/user").get())
      withClue("Response body: " + getDummyUserByInvalidatedTokenResp.body) {
        getDummyUserByInvalidatedTokenResp.status mustBe (BAD_REQUEST)
      }
      (getDummyUserByInvalidatedTokenResp.json \ "error").as[String] mustBe "invalid_grant"
    }
  }

  "multi-tenant AuthenticationController" must {
    "issue token to user from root organization using org alias" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(user1.username),
          "password" -> Seq(USER1_PASSWORD),
          "organization_id" -> Seq("root")
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      (resp.json \ "access_token").as[String] must not be (empty)
    }


    "not issue token to user if specified organization id is invalid" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(user1.username),
          "password" -> Seq(USER1_PASSWORD),
          "organization_id" -> Seq("fake_org")
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_grant"
      (resp.json \ "error_description").as[String] mustBe "Invalid credentials"
    }

    "not issue token to user from different organization" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(user1.username),
          "password" -> Seq(USER1_PASSWORD),
          "organization_id" -> Seq(org1.id)
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_grant"
      (resp.json \ "error_description").as[String] mustBe "Invalid credentials"
    }

    "issue token to user from sub-organization when no organization id specified" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(org1user1.username),
          "password" -> Seq(IntegrationTestUtils.DEFAULT_PWD)
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }
      (resp.json \ "access_token").as[String] must not be (empty)
    }

    "issue token to user from sub-organization when correct organization id specified" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(org1user1.username),
          "password" -> Seq(IntegrationTestUtils.DEFAULT_PWD),
          "organization_id" -> Seq(org1.id)
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val token = (resp.json \ "access_token").as[String]
      token must not be (empty)
    }


    "not issue token to inactive organization user when org id is not specified" in {
      itUtils.changeOrgStatus(org2.id, OrganizationStatus.INACTIVE)

      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(org2user1.username),
          "password" -> Seq(IntegrationTestUtils.DEFAULT_PWD)
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_grant"
      (resp.json \ "error_description").as[String] mustBe "Invalid credentials"
    }

    "not issue token to inactive organization user when org id is specified" in {
      itUtils.changeOrgStatus(org2.id, OrganizationStatus.INACTIVE)

      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(org2user1.username),
          "password" -> Seq(IntegrationTestUtils.DEFAULT_PWD),
          "organization_id" -> Seq(org2.id)
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(BAD_REQUEST) }
      (resp.json \ "error").as[String] mustBe "invalid_grant"
      (resp.json \ "error_description").as[String] mustBe "Invalid credentials"
    }

    "issue token after activating organization" in {
      await(orgService.enable(org2.id))

      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(org2user1.username),
          "password" -> Seq(IntegrationTestUtils.DEFAULT_PWD),
          "organization_id" -> Seq(org2.id)
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val token = (resp.json \ "access_token").as[String]
      token must not be (empty)
    }

    "issue token to user with duplicate name from org1 distinguished by password" in {
      org1dupUser
      org2dupUser

      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(org1dupUser.username),
          "password" -> Seq("org1")
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val token = (resp.json \ "access_token").as[String]
      token must not be (empty)
      await(authenticationService.validateToken(token)).value.organizationId must be (org1.id)
    }

    "issue token to user with duplicate name from org2 distinguished by password" in {
      val resp = await(WS.url(TOKEN_BASE_URL).
        post(Map("grant_type" -> Seq("password"),
          "username" -> Seq(org1dupUser.username),
          "password" -> Seq("org2")
      )))
      withClue("Response body: "+resp.body) { resp.status mustBe(OK) }

      val token = (resp.json \ "access_token").as[String]
      token must not be (empty)
      await(authenticationService.validateToken(token)).value.organizationId must be (org2.id)
    }
  }
}
