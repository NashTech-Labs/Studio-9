package com.sentrana.umserver.services

import com.sentrana.umserver.entities.UserEntity
import com.sentrana.umserver.exceptions.ItemNotFoundException
import com.sentrana.umserver.shared.dtos.enums.UserStatus
import com.sentrana.umserver.{OneAppWithMongo, WithAdminUser}
import org.scalatestplus.play.PlaySpec
import play.api.test.FakeApplication
import play.api.test.Helpers._

/**
  * Created by Alexander on 23.08.2016.
  */
class UserQueryServiceSpec extends PlaySpec with OneAppWithMongo with WithAdminUser {

  private val testSenderName = "testSenderName"
  private val testSenderAddress = "testEmailSupport@domain.test"

  private val emailToSearch = "sample@email.com"

  private val userServiceSpecConfig = Map("password.min.length" -> "5",
    "password.max.length" -> "10",
    "umserver.email.sender.name" -> testSenderName,
    "umserver.email.sender.address" -> testSenderAddress,
    "ui.application.url" -> "http://localhost",
    "umserver.password.reset.link.timeout" -> "30 h")


  override protected def additionalConfig: Map[String, _] = super.additionalConfig ++ userServiceSpecConfig
  private lazy val userQueryService = app.injector.instanceOf(classOf[UserQueryService])
  private lazy val userService = app.injector.instanceOf(classOf[UserService])
  private var userId: String = _

  "UserQueryService" must {

    "find active user by email" in {
      userId = itUtils.createTestUser(userName = "userToSearchByEmail", email = Option(emailToSearch), orgId = rootOrg.id).id
      val user: UserEntity = await(userQueryService.findActiveUser(emailToSearch))
      user.id mustBe userId
      user.status mustBe UserStatus.ACTIVE
    }

    "not find user find inactive user" in {
      await(userService.updateUserStatus(rootOrg.id, userId, UserStatus.INACTIVE))
      intercept[ItemNotFoundException] {
        await(userQueryService.findActiveUser(emailToSearch))
      }
      ()
    }

    "not find deleted user" in {
      await(userService.delete(rootOrg.id, userId))
      intercept[ItemNotFoundException] {
        await(userQueryService.findActiveUser(emailToSearch))
      }
      ()
    }
  }
}
