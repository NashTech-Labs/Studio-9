package com.sentrana.umserver

import com.sentrana.umserver.services.UserConverter
import org.scalatestplus.play.PlaySpec
import play.api.http.{HeaderNames, Status}
import play.api.libs.ws.WS
import play.api.mvc.EssentialAction
import play.api.test.Helpers._
import play.api.test.{FakeRequest, ResultExtractors}

/**
  * Created by Alexander on 02.08.2016.
  */
class CookiesUsageSpec extends PlaySpec with OneServerWithMongo with WithAdminUser with ResultExtractors with HeaderNames with Status {

  private def usersBaseUrl(orgId: String) = s"$baseUrl/orgs/${orgId}/users"

  private lazy val user = itUtils.createTestUser("testUser")

  private lazy val userConverter = app.injector.instanceOf(classOf[UserConverter])
  private lazy val controllerWithDisabledCookies = new CookiesRestrictionController(rootOrg, userConverter.toUserDto(user))

  private lazy val controllerWithEnabledCookies = new CookiesRestrictionController(rootOrg, userConverter.toUserDto(user)) {
    override protected def authCookieEnabled: Boolean = true
  }

  "ControllerWithDisabledCookies" must {
    "read user with token from query string" in {
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + "/" + user.id).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + resp.body) {
        resp.status mustBe (OK)
      }
      (resp.json \ "id").as[String] mustBe (user.id)
    }

    "not read user with token in cookies" in {
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + "/" + user.id).withHeaders("Cookie" -> s"access_token=${adminToken}").get())
      withClue("Response body: " + resp.body) {
        resp.status mustBe (UNAUTHORIZED)
      }
    }

    "not read user with default restricted cookies" in {
      val action: EssentialAction = controllerWithDisabledCookies.methodWithDefaultCookiesRestriction()
      val request = FakeRequest(POST, "/").withHeaders("Cookie" -> s"access_token=${adminToken}")

      val result = call(action, request)
      await(result).header.status mustBe UNAUTHORIZED
    }

    "not read user with restricted cookies" in {
      val action: EssentialAction = controllerWithDisabledCookies.methodWithDisabledCookies()
      val request = FakeRequest(POST, "/").withHeaders("Cookie" -> s"access_token=${adminToken}")

      val result = call(action, request)
      await(result).header.status mustBe UNAUTHORIZED
    }

    "read user with allowed cookies" in {
      val action: EssentialAction = controllerWithDisabledCookies.methodWithEnabledCookies()
      val request = FakeRequest(POST, "/").withHeaders("Cookie" -> s"access_token=${adminToken}")

      val result = call(action, request)
      await(result).header.status mustBe OK
    }
  }

  "ControllerWithEnabledCookies" must {
    "read user with token from query string" in {
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + "/" + user.id).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + resp.body) {
        resp.status mustBe (OK)
      }
      (resp.json \ "id").as[String] mustBe (user.id)
    }

    "read user with token in cookies" in {
      val resp = await(WS.url(usersBaseUrl(rootOrg.id) + "/" + user.id).withHeaders("Cookie" -> s"access_token=${adminToken}").get())
      withClue("Response body: " + resp.body) {
        resp.status mustBe (UNAUTHORIZED)
      }
    }

    "read user with default cookies restrictions" in {
      val action: EssentialAction = controllerWithEnabledCookies.methodWithDefaultCookiesRestriction()
      val request = FakeRequest(POST, "/").withHeaders("Cookie" -> s"access_token=${adminToken}")

      val result = call(action, request)
      await(result).header.status mustBe OK
    }

    "not read user with cookies restriction" in {
      val action: EssentialAction = controllerWithEnabledCookies.methodWithDisabledCookies()
      val request = FakeRequest(POST, "/").withHeaders("Cookie" -> s"access_token=${adminToken}")

      val result = call(action, request)
      await(result).header.status mustBe UNAUTHORIZED
    }

    "read user with allowed cookies" in {
      val action: EssentialAction = controllerWithEnabledCookies.methodWithEnabledCookies()
      val request = FakeRequest(POST, "/").withHeaders("Cookie" -> s"access_token=${adminToken}")

      val result = call(action, request)
      await(result).header.status mustBe OK
    }
  }
}
