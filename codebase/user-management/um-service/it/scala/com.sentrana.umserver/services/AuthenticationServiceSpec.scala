package com.sentrana.umserver.services

import com.sentrana.umserver.entities.UserLoginRecord
import com.sentrana.umserver.shared.dtos.enums.{OrganizationStatus, UserStatus}
import com.sentrana.umserver.{IntegrationTestUtils, OneAppWithMongo}
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._

/**
  * Created by Paul Lysak on 14.04.16.
  */
class AuthenticationServiceSpec extends PlaySpec with OneAppWithMongo {
  private val IT_PREFIX = "integrationTest_"
  private val FAKE_USER_NAME = "noSuchUser"
  private val USER1_PASSWORD = "myPassword"
  private val USER2_PASSWORD = "myPassword2"
  private val LOGIN_IP = "localhost"

  private lazy val rootOrg = itUtils.createRootOrg()
  private lazy val org1 = itUtils.createTestOrg("org1", rootOrg.id)

  private lazy val user1 = itUtils.createTestUser(IT_PREFIX + "sampleUser1", USER1_PASSWORD)
  private lazy val user2 = itUtils.createTestUser(IT_PREFIX + "sampleUser2", USER2_PASSWORD)
  private lazy val user3 = itUtils.createTestUser(IT_PREFIX + "sampleUser3", USER2_PASSWORD)
  private lazy val user4 = itUtils.createTestUser(IT_PREFIX + "sampleUser4", USER2_PASSWORD)
  private lazy val org1user1 = itUtils.createTestUser(IT_PREFIX + "org1user1", USER2_PASSWORD, orgId = org1.id)
  private lazy val user1Token = authService.issueToken(user1)._1
  private lazy val user3Token = authService.issueToken(user3)._1
  private lazy val user4Token = authService.issueToken(user4)._1
  private lazy val org1user1Token = authService.issueToken(org1user1)._1

  private lazy val itUtils = new IntegrationTestUtils()
  private lazy val userService = app.injector.instanceOf(classOf[UserService])
  private lazy val authService = app.injector.instanceOf(classOf[AuthenticationService])

  private lazy implicit val mongoDbService = app.injector.instanceOf(classOf[MongoDbService])

  "AuthenticationService" must {
    "validate valid token" in {
      rootOrg

      val u = await(authService.validateToken(user1Token)).value
      u.username must be (user1.username)
      u.id mustBe(user1.id)
    }

    "not validate invalid token" in {
      await(authService.validateToken("123412341234")) must be (empty)
    }

    "sign in user with correct password, org is specified explicitly" in {
      val res = await(authService.userSignIn(UserName(org1user1.username), USER2_PASSWORD, orgIdOpt = None, remoteAddress = "127.0.0.1"))
      val t = res.value._1
      val u = await(authService.validateToken(t)).value
      u.id must be(org1user1.id)
    }

    "sign in user with correct password, org is inferred automatically" in {
      val res = await(authService.userSignIn(UserName(org1user1.username), USER2_PASSWORD, orgIdOpt = Option(org1.id), remoteAddress = "127.0.0.1"))
      val t = res.value._1
      val u = await(authService.validateToken(t)).value
      u.id must be(org1user1.id)
    }

    "invalidate token if user is deactivated" in {
      await(authService.validateToken(user3Token)).value
      await(userService.updateUserStatus(rootOrg.id, user3.id, UserStatus.INACTIVE))

      await(authService.validateToken(user3Token)) must be (empty)
    }

    "invalidate token if organization is deactivated" in {
      await(authService.validateToken(org1user1Token)).value
      itUtils.changeOrgStatus(org1.id, OrganizationStatus.INACTIVE)

      await(authService.validateToken(org1user1Token)) must be (empty)
    }

    "not sign in user from deactivated org, org is specified explicitly" in {
      val res = await(authService.userSignIn(UserName(org1user1.username), USER2_PASSWORD, orgIdOpt = None, remoteAddress = "127.0.0.1"))
      res must be (empty)
    }

    "not sign in user from deactivated org, org is inferred automatically" in {
      val res = await(authService.userSignIn(UserName(org1user1.username), USER2_PASSWORD, orgIdOpt = Option(org1.id), remoteAddress = "127.0.0.1"))
      res must be (empty)
    }

    "return valid token for user and save successful login result" in {
      val tokenDuration = await(authService.userSignIn(UserName(user1.username), USER1_PASSWORD, None, LOGIN_IP)).value
      val userEntity = await(authService.validateToken(tokenDuration._1)).value
      userEntity.username mustBe user1.username

      val userLoginRecords = await(authService.getUserLoginRecords(user1.username))
      userLoginRecords.size mustBe 1
      validateUserLoginRecord(userLoginRecords.head, user1.username, true, Option(user1.id), LOGIN_IP)
    }

    "return None for incorrect password login attempt and save failure login result" in {
      val tokenDurationOpt = await(authService.userSignIn(UserName(user2.username), USER1_PASSWORD, None, LOGIN_IP))
      tokenDurationOpt mustBe None

      val userLoginRecords = await(authService.getUserLoginRecords(user2.username))
      userLoginRecords.size mustBe 1
      validateUserLoginRecord(userLoginRecords.head, user2.username, false, Option(user2.id), LOGIN_IP)
    }

    "return None for non existing user login attempt and save failure login result" in {
      val tokenDurationOpt = await(authService.userSignIn(UserName(FAKE_USER_NAME), USER1_PASSWORD, None, LOGIN_IP))
      tokenDurationOpt mustBe None

      val userLoginRecords = await(authService.getUserLoginRecords(FAKE_USER_NAME))
      userLoginRecords.size mustBe 1
      validateUserLoginRecord(userLoginRecords.head, FAKE_USER_NAME, false, None, LOGIN_IP)
    }

    "delete token from cache" in  {
      await(authService.validateToken(user4Token)) must not be empty
      authService.invalidateToken(user4Token)
      await(authService.validateToken(user4Token)) mustBe None
    }
  }

  private def validateUserLoginRecord(userLoginRecord: UserLoginRecord,
                                      expectedLoginUserName: String,
                                      expectedLoginResult: Boolean,
                                      expectedLoginUserId: Option[String],
                                      expectedLoginIp: String) = {
    userLoginRecord.loginResult mustBe expectedLoginResult
    userLoginRecord.loginUserName mustBe expectedLoginUserName
    userLoginRecord.loginIp mustBe expectedLoginIp
    userLoginRecord.loginUserId mustBe expectedLoginUserId
  }
}
