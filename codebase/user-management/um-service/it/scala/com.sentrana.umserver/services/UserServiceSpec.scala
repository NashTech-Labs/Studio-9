package com.sentrana.umserver.services

import java.time.ZonedDateTime
import java.util.UUID

import com.sentrana.umserver.dtos.{ CreateUserRequest, UserDeactivationRequest }
import com.sentrana.umserver.entities.{ PasswordReset, UserEntity }
import com.sentrana.umserver.exceptions._
import com.sentrana.umserver.shared.dtos.enums.{ PasswordResetStatus, UserStatus }
import com.sentrana.umserver.shared.dtos.{ UpdatePasswordRequest, UserPasswordHistory }
import com.sentrana.umserver.utils.PasswordHash
import com.sentrana.umserver.{ OneAppWithMongo, UmSettings, WithAdminUser }
import org.mongodb.scala.bson.collection.immutable.Document
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.play.PlaySpec
import play.api.cache.CacheApi
import play.api.test.Helpers._

/**
  * Created by Paul Lysak on 17.06.16.
  */
class UserServiceSpec extends PlaySpec
  with OneAppWithMongo
  with WithAdminUser
  with TableDrivenPropertyChecks {

  private val userServiceSpecConfig = Map(
    "umserver.password.validation.min.length" -> "5",
    "umserver.password.validation.max.length" -> "10"
  )

  override protected def additionalConfig: Map[String, _] = super.additionalConfig ++ userServiceSpecConfig

  private lazy val userService = app.injector.instanceOf(classOf[UserService])
  private lazy val umSettings = app.injector.instanceOf(classOf[UmSettings])

  implicit private lazy val mongoDbService = app.injector.instanceOf(classOf[MongoDbService])
  private lazy val userQueryService = app.injector.instanceOf(classOf[UserQueryService])
  private lazy val cache = app.injector.instanceOf(classOf[CacheApi])

  private lazy val org1 = itUtils.createTestOrg("org1", rootOrg.id)
  private lazy val org2 = itUtils.createTestOrg("org1", rootOrg.id)

  private lazy val rg1 = itUtils.createTestGroup("rg1")
  private lazy val rg2s = itUtils.createTestGroup("rg2s", forChildOrgs = true)
  private lazy val o1g1 = itUtils.createTestGroup("o1g1", orgId = org1.id)
  private lazy val o2g1 = itUtils.createTestGroup("o2g1", orgId = org2.id)
  private lazy val o2g2s = itUtils.createTestGroup("o2g2s", orgId = org2.id, forChildOrgs = true)

  private lazy val userToChangeForgottenPassword = itUtils.createTestUser("userToChangeForgottenPassword")
  private lazy val userToChangePassword = itUtils.createTestUser("userToChangePassword", oldPassword, org1.id)
  private val emailToResetPassword = "emailToResetPassword@test.test"
  private var oldPassword = "oldPswd"
  private val emailToSearch = "sample@email.com"
  private val previousPasswords = Array("prevP1", "prevP2", "prevP3", "prevP4", "prevP5")
  private val secretCode = "secretCode"
  private var userId: String = _
  var userToResetPassword: UserEntity = _

  private val createReqTemplate = CreateUserRequest(username = "sampleUser",
    email = emailToSearch,
    password = "samplePwd",
    firstName = "First",
    lastName = "Last",
    groupIds = Set.empty
  )

  "UserService" must {
    "not create user in regular org with root org group" in {
      intercept[ValidationException] {
        await(userService.create(org1.id, createReqTemplate.copy(groupIds = Set(rg1.id))))
      }
      ()
    }

    "not create user in regular org with other org group" in {
      intercept[ValidationException] {
        await(userService.create(org1.id, createReqTemplate.copy(groupIds = Set(o2g1.id))))
      }
      ()
    }

    "not create user in regular org with other org group even if forChildOrgs is true" in {
      intercept[ValidationException] {
        await(userService.create(org1.id, createReqTemplate.copy(groupIds = Set(o2g2s.id))))
      }
      ()
    }

    "not create user in root org with child org group" in {
      intercept[ValidationException] {
        await(userService.create(rootOrg.id, createReqTemplate.copy(groupIds = Set(o1g1.id))))
      }
      ()
    }

    "not create user if one with the same email already exists" in {
      await(userService.create(org1.id, createReqTemplate.copy(
        groupIds = Set(rg2s.id),
        email = "recipient@example.com",
        username = s"recipient-${UUID.randomUUID()}"
      )))

      val emails = Table(
        "email",
        "recipient@example.com",
        "RECIPIENT@EXAMPLE.COM",
        "recipient@EXAMPLE.COM",
        "RECIPIENT@example.com",
        "rEcIPiEnT@eXaMPle.cOm",
        "recipient@eXaMPle.cOm",
        "rEcIPiEnT@example.com"
      )

      forAll(emails) { email =>
        val exception = intercept[ValidationException] {
          await(userService.create(org1.id, createReqTemplate.copy(
            groupIds = Set(rg2s.id),
            email = email,
            username = s"recipient-${UUID.randomUUID()}"
          )))
        }
        exception.getMessage must be (s"User email $email is not unique")
        ()
      }
    }

    "create user in regular org with root org's group with forChildOrgs=true" in {
      val entity = await(userService.create(org1.id, createReqTemplate.copy(groupIds = Set(rg2s.id))))
      userId = entity.id
      val actualUser = await(userQueryService.getMandatory(rootOrg.id, entity.id))
      actualUser.username must be (createReqTemplate.username)
      actualUser.groupIds must contain only (rg2s.id)
    }

    "fail activate user when user is activated" in {
      intercept[ValidationException]{
        await(userService.activateUser(org1.id, userId))
      }
      ()
    }

    "deactivate user when user is activated" in {
      val entity = await(userService.deactivateUser(org1.id, userId, UserDeactivationRequest(None)))
      entity.status must be (UserStatus.DEACTIVATED)
    }

    "fail deactivate user when user is deactivated" in {
      intercept[ValidationException]{
        await(userService.deactivateUser(org1.id, userId, UserDeactivationRequest(None)))
      }
      ()
    }

    "activate user when user is deactivated" in {
      val entity = await(userService.activateUser(org1.id, userId))
      entity.status must be (UserStatus.ACTIVE)
    }

    "pass current users password via UpdatePasswordRequest" in {
      intercept[AuthenticationException]{
        await(userService.updatePassword(org1.id, userToChangePassword.id, UpdatePasswordRequest("121", "")))
      }
      ()
    }

    "not allow empty password" in {
      intercept[EmptyPasswordException]{
        await(userService.updatePassword(org1.id, userToChangePassword.id, UpdatePasswordRequest(oldPassword, "")))
      }
      ()
    }

    "not change password with the size less then minimum" in {
      intercept[InvalidPasswordLengthException] {
        await(userService.updatePassword(org1.id, userToChangePassword.id, UpdatePasswordRequest(oldPassword, "1234")))
      }
      ()
    }

    "not change password with the size longer then maximum" in {
      intercept[InvalidPasswordLengthException] {
        await(userService.updatePassword(org1.id, userToChangePassword.id, UpdatePasswordRequest(oldPassword, "12345678901")))
      }
      ()
    }

    "allow password with more than two duplicate characters in a raw" in {
      Array("1233444567", "123ppp", "ppp123", "1ppp123").foreach { password =>
        intercept[OwaspPasswordFormatException] {
          await(userService.updatePassword(org1.id, userToChangePassword.id, UpdatePasswordRequest(oldPassword, password)))
        }
      }
    }

    "add previously used passwords" in {
      import com.sentrana.umserver.entities.MongoFormats.userPasswordHistoryMongoFormat
      previousPasswords.foreach { password =>
        await(mongoDbService.save[UserPasswordHistory](
          UserPasswordHistory(mongoDbService.generateId, userToChangePassword.id, PasswordHash.create(password).toBase64String))
        )
      }
    }

    "not allow to use last 5 previous password" in {
      previousPasswords.foreach { password =>
        intercept[PreviouslyUsedPasswordException] {
          await(userService.updatePassword(org1.id, userToChangePassword.id, UpdatePasswordRequest(oldPassword, password)))
        }
      }
    }

    "not allow 1 OWASP complexity rule out of 4" in {
      Array("12345", "abcde", "ABCDE", "!@#$%").foreach { password =>
        intercept[OwaspPasswordFormatException] {
          await(userService.updatePassword(org1.id, userToChangePassword.id, UpdatePasswordRequest(oldPassword, password)))
        }
      }
    }

    "not allow 2 OWASP complexity rules out of 4" in {
      Array("1bcde", "1BCDE", "1@#$%", "aBCDE", "a@#$%", "A@#$%").foreach { password =>
        intercept[OwaspPasswordFormatException] {
          await(userService.updatePassword(org1.id, userToChangePassword.id, UpdatePasswordRequest(oldPassword, password)))
        }
      }
    }

    "allow 3 OWASP complexity rules out of 4" in {
      Array("1bCDE", "1B#$%", "1a#$%", "aBCD$").foreach { password =>
        await(userService.updatePassword(org1.id, userToChangePassword.id, UpdatePasswordRequest(oldPassword, password)))
        oldPassword = password
        PasswordHash.parse(await(userQueryService.getMandatory(org1.id, userToChangePassword.id)).password).checkPassword(password) mustBe true
      }
    }

    "create resetPasswordRequest" in {
      import com.sentrana.umserver.entities.MongoFormats.passwordResetMongoFormat
      userToResetPassword = itUtils.createTestUser(userName = "userToResetPassword", email = Option(emailToResetPassword))

      await(userService.resetPassword(userToResetPassword))

      val passwordReset: PasswordReset =
        await(mongoDbService.find[PasswordReset](Document(s"""{"email":"$emailToResetPassword"}""")).toFuture()).headOption.value
      passwordReset.status mustBe PasswordResetStatus.ACTIVE
      passwordReset.email mustBe emailToResetPassword
      passwordReset.secretCode must not be empty
    }

    "not allow to change password if there is no PasswordResetRequest for the user" in {
      intercept[AuthenticationException] {
        await(userService.updateForgottenPassword(userToChangeForgottenPassword.id, secretCode, "newPassword"))
      }
      ()
    }

    "not allow to change password if latest PasswordResetRequest is not ACTIVE" in {
      itUtils.createTestPasswordReset(userToChangeForgottenPassword.id,
        userToChangeForgottenPassword.email,
        secretCode,
        PasswordResetStatus.INVALIDATED)

      intercept[AuthenticationException] {
        await(userService.updateForgottenPassword(userToChangeForgottenPassword.id, secretCode, "newPassword"))
      }
      ()
    }

    "not allow to change password if secretCode doesn't match" in {
      import com.sentrana.umserver.entities.MongoFormats.passwordResetMongoFormat
      val passwordReset = itUtils.createTestPasswordReset(userToChangeForgottenPassword.id,
        userToChangeForgottenPassword.email,
        secretCode + "fake",
        PasswordResetStatus.ACTIVE)

      intercept[AuthenticationException] {
        await(userService.updateForgottenPassword(userToChangeForgottenPassword.id, secretCode, "newPassword"))
      }
      await(mongoDbService.delete[PasswordReset](passwordReset.id, OrgScopeRoot))
      cache.remove(UserService.passwordUpdateAttemptKey(userToChangeForgottenPassword.email))
      ()
    }

    "not allow to change password if it doesn't pass complexity rules validation" in {
      import com.sentrana.umserver.entities.MongoFormats.passwordResetMongoFormat
      val passwordResetRequest = itUtils.createTestPasswordReset(userToChangeForgottenPassword.id,
        userToChangeForgottenPassword.email,
        secretCode,
        PasswordResetStatus.ACTIVE)

      intercept[OwaspPasswordFormatException] {
        await(userService.updateForgottenPassword(userToChangeForgottenPassword.id, secretCode, "newPwd"))
      }
      await(mongoDbService.delete[PasswordReset](passwordResetRequest.id, OrgScopeRoot))
      ()
    }

    "not change password because of secretCode expiration" in {
      import com.sentrana.umserver.entities.MongoFormats.passwordResetMongoFormat
      import org.mongodb.scala.model.Filters.{ equal => mongoEqual }
      val passwordReset = itUtils.createTestPasswordReset(userToChangeForgottenPassword.id,
        userToChangeForgottenPassword.email,
        secretCode,
        PasswordResetStatus.ACTIVE,
        ZonedDateTime.now().minusHours(umSettings.passwordReset.linkLifetime.toHours + 1)
      )

      val newPassword = "newTest12!"
      intercept[AuthenticationException] {
        await(userService.updateForgottenPassword(userToChangeForgottenPassword.id, secretCode, newPassword))
      }

      val userPasswordResetRequest = await(mongoDbService.find[PasswordReset](mongoEqual("id", passwordReset.id)).toFuture()).head
      userPasswordResetRequest.status mustBe PasswordResetStatus.ACTIVE
      await(mongoDbService.delete[PasswordReset](passwordReset.id, OrgScopeRoot))
      ()
    }

    "change password and update PasswordResetRequest" in {
      import com.sentrana.umserver.entities.MongoFormats.passwordResetMongoFormat
      import org.mongodb.scala.model.Filters.{ equal => mongoEqual }
      val passwordResetRequest = itUtils.createTestPasswordReset(userToChangeForgottenPassword.id,
        userToChangeForgottenPassword.email,
        secretCode,
        PasswordResetStatus.ACTIVE)

      val newPassword = "newPwd12!"
      val updatedUser = await(userService.updateForgottenPassword(userToChangeForgottenPassword.id, secretCode, newPassword))

      val userdPasswordResetRequest = await(mongoDbService.find[PasswordReset](mongoEqual("id", passwordResetRequest.id)).toFuture()).head
      userdPasswordResetRequest.status mustBe PasswordResetStatus.USED
      PasswordHash.parse(updatedUser.password).checkPassword(newPassword) mustBe true
    }

    "allow only one Active PasswordReset" in {
      import com.sentrana.umserver.entities.MongoFormats.passwordResetMongoFormat
      import org.mongodb.scala.model.Filters.{ equal => mongoEqual }
      val requestFirst = await(userService.resetPassword(userToChangeForgottenPassword))
      await(mongoDbService.update[PasswordReset](requestFirst.copy(created = requestFirst.created.minusMinutes(40)), OrgScopeRoot))

      val requestSecond = await(userService.resetPassword(userToChangeForgottenPassword))
      await(mongoDbService.update[PasswordReset](requestSecond.copy(created = requestSecond.created.minusMinutes(30)), OrgScopeRoot))

      val requestThird = await(userService.resetPassword(userToChangeForgottenPassword))
      await(mongoDbService.update[PasswordReset](requestThird.copy(created = requestThird.created.minusMinutes(20)), OrgScopeRoot))

      val passwordResetRequests = await(mongoDbService.find[PasswordReset](mongoEqual("userId", userToChangeForgottenPassword.id)).toFuture())
      passwordResetRequests.count(_.status == PasswordResetStatus.ACTIVE) mustBe 1
      passwordResetRequests.count(_.status == PasswordResetStatus.USED) mustBe 1
      passwordResetRequests.count(_.status == PasswordResetStatus.INVALIDATED) mustBe 3
    }
  }
}
