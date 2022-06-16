package com.sentrana.um.acceptance

import java.time.ZoneId

import com.sentrana.um.client.play.UmClientImpl
import com.sentrana.um.client.play.exceptions.{UmAuthenticationException, UmAccessDeniedException, UmServerException, UmValidationException}
import com.sentrana.umserver.shared.dtos.DataFilterInstance
import com.sentrana.umserver.shared.dtos.enums.FilterOperator
import org.scalatest.{DoNotDiscover, BeforeAndAfterAll}
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.Configuration
import play.api.test.Helpers._

/**
  * Created by Paul Lysak on 21.04.16.
  */
class UmClientSpec extends PlaySpec with OneAppPerSuite with WithUmClient with WithEmailDumbster {
  private lazy val cfg = app.injector.instanceOf(classOf[Configuration])

  private lazy val umClientWrongCredentials = new UmClientImpl(umServiceUrl,
    SampleData.apps.defaultApp.id,
    "wrongSecret",
    cache,
    wsClient)

  private var token1: String = _

  "UmClient" must {
    "sign in user with correct password" in {
      SampleData.init(cfg)

      val (token, lifetime) = await(umClient.signIn(SampleData.users.admin1.username, SampleData.users.admin1.passwordPlain)).value
      token1 = token
      token must not be (empty)
      lifetime must be >0
    }

    "sign in user by email" in {
      val (token, lifetime) = await(umClient.signInByEmail(SampleData.users.admin1.email, SampleData.users.admin1.passwordPlain)).value
      token1 = token
      token must not be (empty)
      lifetime must be >0
    }

    "don't sign in user with incorrect password" in {
      val tokenOpt = await(umClient.signIn(SampleData.users.admin1.username, "wrongPwd"))
      tokenOpt must be (empty)
    }

    "don't sign in user with incorrect username" in {
      val tokenOpt = await(umClient.signIn("wrongUser", SampleData.users.admin1.passwordPlain))
      tokenOpt must be (empty)
    }

    "validate valid token" in {
      val user = await(umClient.validateAccessToken(token1)).value
      user.username must be (SampleData.users.admin1.username)
    }

    "don't validate invalid token" in {
      val userOpt = await(umClient.validateAccessToken("someFakeToken"))
      userOpt must be (empty)
    }

    "get root org" in {
      val rootOrg = await(umClient.getRootOrg())
      rootOrg.id must be (SampleData.orgs.root.id)
      rootOrg.name must be (SampleData.orgs.root.name)
    }

    "get application token" in {
      val applicationToken = await(umClient.getApplicationAccessToken())
      applicationToken must not be empty
    }

    "throw UmServerException for login with wrong credentials" in {
      await(umClientWrongCredentials.getApplicationAccessToken()) mustBe None
    }

    "get user by userId" in {
      val expectedAdminUser = SampleData.users.admin1
      val actualAdminUser = await(umClient.getUser(expectedAdminUser.id)).value
      actualAdminUser.id mustBe expectedAdminUser.id
      actualAdminUser.firstName mustBe expectedAdminUser.firstName
      actualAdminUser.lastName mustBe expectedAdminUser.lastName
      actualAdminUser.username mustBe expectedAdminUser.username
      actualAdminUser.email mustBe expectedAdminUser.email
    }

    "get user with org details" in {
      val expectedAdminUser = SampleData.users.admin1
      val expectedOrg = SampleData.orgs.root
      val actualAdminUser = await(umClient.getUser(expectedAdminUser.id, withOrgDetails = true)).value
      actualAdminUser.id mustBe expectedAdminUser.id
      val actualOrg = actualAdminUser.organization.value
      actualOrg.id mustBe expectedOrg.id
      actualOrg.name mustBe expectedOrg.name
    }

    "get user with time zone conversion" in {
      val expectedAdminUser = SampleData.users.admin1
      val actualAdminUser = await(umClient.getUser(expectedAdminUser.id, withTimeZone = Option("+02:00"))).value
      actualAdminUser.id mustBe expectedAdminUser.id
      actualAdminUser.created.getZone mustBe ZoneId.of("+02:00")
    }

    "find user by username" in {
      val u = SampleData.users.org1Admin1
      val actualUsers = await(umClient.findUsers(username = Option(u.username)))
      actualUsers.map(_.username) must contain only(u.username)
    }

    "find user by email" in {
      val u = SampleData.users.emailQweUser
      val actualUsers = await(umClient.findUsers(email = Option(u.email)))
      actualUsers.map(_.username) must contain only(u.username)
    }

    "find user by email prefix" in {
      import SampleData.users._
      val actualUsers = await(umClient.findUsers(emailPrefix = Option("qwe")))
      actualUsers.map(_.username) must contain only(emailQweUser.username, emailQwertyUser.username)
    }

    "find user by orgId" in {
      import SampleData.users._
      val actualUsers = await(umClient.findUsers(orgId = Option(SampleData.orgs.orgForSearch.id)))
      actualUsers.map(_.username) must contain only(userForOrgSearch.username)
    }

    "get group by id" in {
      val expectedUserGroup = SampleData.groups.superUsers
      val actualUserGroup = await(umClient.getUserGroup(expectedUserGroup.id)).value
      actualUserGroup.id mustBe expectedUserGroup.id
      actualUserGroup.name mustBe expectedUserGroup.name
      actualUserGroup.grantsPermissions mustBe expectedUserGroup.grantsPermissions
      actualUserGroup.forChildOrgs mustBe expectedUserGroup.forChildOrgs
      actualUserGroup.organizationId mustBe expectedUserGroup.organizationId
      actualUserGroup.parentGroupId mustBe expectedUserGroup.parentGroupId
    }

    "get group by non existing id" in {
      val fakeGroupId = "fake" + SampleData.groups.superUsers.id
      await(umClient.getUserGroup(fakeGroupId)) mustBe None
    }

    "get group for unauthorized application" in {
      intercept[UmServerException] {
        await(umClientWrongCredentials.getUserGroup(SampleData.groups.superUsers.id))
      }
      ()
    }

    "not get user by non existing id" in {
      val fakeAdminId = "fake" + SampleData.users.admin1.id
      await(umClient.getUser(fakeAdminId)) mustBe None
    }

    "get user for unauthorized application" in {
      intercept[UmServerException] {
        await(umClientWrongCredentials.getUserGroup(SampleData.groups.superUsers.id))
      }
      ()
    }

    "find all filters" in {
      val filters = await(umClient.findFilters())
      filters.map(f => (f.id, f.fieldName)) must contain only (SampleData.dataFilters.all.map(f => (f.id, f.fieldName)): _*)
    }

    "find filters by field name" in {
      val sf = SampleData.dataFilters.sampleDataFilterInfo1
      val filters = await(umClient.findFilters(fieldName = Option(sf.fieldName)))
      filters.map(f => (f.id, f.fieldName)) must contain only ((sf.id, sf.fieldName))
    }

    "return map of fieldNames, dataFilterInstances by filterIds" in {
      val expectedDataFiltersUser = SampleData.users.dataFiltersUser
      val actualAdminUser = await(umClient.getUser(expectedDataFiltersUser.id)).value
      actualAdminUser.id mustBe expectedDataFiltersUser.id
      val fieldNameFilterInstances: Map[String, DataFilterInstance] =  await(umClient.getFilterInstances(actualAdminUser.id))
      fieldNameFilterInstances.size mustBe 3
      fieldNameFilterInstances.map(_._1) must contain only(SampleData.dataFilters.all.map(_.fieldName) :_*)
    }

    "define filter instance for user" in {
      val fi = DataFilterInstance(SampleData.dataFilters.sampleDataFilterInfo1.id, FilterOperator.IN, Set("QQQ"))
      val sampleUser = await(umClient.getUserMandatory(SampleData.users.filterInstanceCreationUser.id))
      await(umClient.setFilterInstance(sampleUser, fi, token1))
      val sampleUser2 = await(umClient.getUserMandatory(SampleData.users.filterInstanceCreationUser.id))
      val actualFis = await(umClient.getFilterInstances(SampleData.users.filterInstanceCreationUser.id))
      actualFis must be (Map(SampleData.dataFilters.sampleDataFilterInfo1.fieldName -> fi))
    }

    "sign up new user" in {
      import scala.collection.JavaConversions._

      val user = SampleData.users.emailQwertyUser
      dumbster.reset()
      val newUserId = await(umClient.signUp(SampleData.orgs.orgSelfServiceSignUp.id,
        user.username + "_new",
        "new_"+user.email,
        user.passwordPlain,
        user.firstName,
        user.lastName))

      newUserId must not be empty
      val emails = dumbster.getReceivedEmails.toSeq
      emails.size must be (1)
    }

    "re-send activation link" in {
      import scala.collection.JavaConversions._

      dumbster.reset()
      await(umClient.reSendActivationLink("new_"+SampleData.users.emailQwertyUser.email))
      val emails = dumbster.getReceivedEmails.toSeq
      emails.size must be (1)
    }

    "not sign-up duplicate user" in {
      val user = SampleData.users.emailQwertyUser
      intercept[UmValidationException] {
        await(umClient.signUp(SampleData.orgs.orgSelfServiceSignUp.id,
        user.username + "_new",
        "new_" + user.email,
        user.passwordPlain,
        user.firstName,
        user.lastName))
      }
      ()
    }

    "not reset password for unknown email" in {
      import scala.collection.JavaConversions._
      import scala.concurrent.ExecutionContext.Implicits.global
      dumbster.reset()
      await(umClient.initPasswordReset("some_fake_email@server.com"))
      dumbster.getReceivedEmails.toSeq must be (empty)
      ()
    }

    "reset password for known email" in {
      import scala.collection.JavaConversions._

      val user = SampleData.users.pwdResetUser
      dumbster.reset()
      await(umClient.initPasswordReset(user.email))
      val emails = dumbster.getReceivedEmails.toSeq
      emails.size must be (1)
      val email = emails.head
      email.getHeaderValue("To") must be (s"${user.firstName} <${user.email}>")


      val PwdLink = """\?secretCode=([a-z\d\-]+)&email=([a-zA-Z\d@\.]+)""".r
      val secretCode: String = PwdLink.findFirstMatchIn(email.getBody) match {
        case Some(PwdLink(secretCode, linkEmail)) =>
          linkEmail must be (user.email)
          secretCode must not be empty
          secretCode
        case None =>
          fail("Password reset link not found in email body")
      }


      val newPwd = "someNewPassword1"

      await(umClient.signInByEmail(user.email, newPwd)) must be (empty)

      await(umClient.completePasswordReset(email = user.email, secretCode = secretCode, newPassword = newPwd))

      await(umClient.signInByEmail(user.email, newPwd)) must not be (empty)
    }

    "not update password if old one is incorrect" in {
      val u = SampleData.users.pwdUpdateUser
      val t = await(umClient.signIn(u.username, u.passwordPlain, None)).value._1
      intercept[UmAuthenticationException] {
        await(umClient.updatePassword(accessToken = t, oldPassword = "someFakePassword", newPassword = "someNewPassword1"))
      }
      ()
    }

    "update password if old one is correct" in {
      val u = SampleData.users.pwdUpdateUser
      val t = await(umClient.signIn(u.username, u.passwordPlain, None)).value._1
      val newPwd = "someNewPassword1"

      await(umClient.signIn(u.username, newPwd)) must be (empty)
      await(umClient.updatePassword(accessToken = t, oldPassword = u.passwordPlain, newPassword = newPwd))
      await(umClient.signIn(u.username, newPwd)) must not be (empty)
    }

  }
}
