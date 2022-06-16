package com.sentrana.um.acceptance

import org.scalatest.DoNotDiscover
import org.scalatestplus.play.{PlaySpec, OneServerPerSuite}
import play.api.Configuration
import play.api.libs.ws.WS
import play.api.test.Helpers._

/**
  * Created by Paul Lysak on 21.04.16.
  */
class ActionProtectionSpec extends PlaySpec with OneServerPerSuite with WithUmClient {
  private val IT_PREFIX = "integrationTest_"

  private lazy val cfg = app.injector.instanceOf(classOf[Configuration])

  private lazy val admin1Token = getToken(SampleData.users.admin1)
  private lazy val noGroupUser1Token = getToken(SampleData.users.noGroupUser1)
  private lazy val noGroupUser2Token = getToken(SampleData.users.noGroupUser2)
  private lazy val sampleGroupUser1Token = getToken(SampleData.users.sampleGroupUser1)
  private lazy val sampleSubgroupUser1Token = getToken(SampleData.users.sampleSubgroupUser1)
  private lazy val anotherGroupUser1Token = getToken(SampleData.users.anotherGroupUser1)

  private lazy val BASE_URL = s"http://localhost:$port/acceptance"

  "SampleController" must {
    "public action - access without token" in {
      SampleData.init(cfg)

      val resp = await(WS.url(BASE_URL + "/publicAction").get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
    }

    "authenticated action - forbid access without access token" in {
      val resp = await(WS.url(BASE_URL + "/authenticatedAction").get())
      withClue("Response body: " + resp.body) { resp.status mustBe (UNAUTHORIZED) }
    }

   "authenticated action - forbid access with invalid access token" in {
      val resp = await(WS.url(BASE_URL + "/authenticatedAction").withQueryString("access_token" -> "someFakeToken").get())
      withClue("Response body: " + resp.body) { resp.status mustBe (UNAUTHORIZED) }
    }

    "authenticated action - access with regular user access token" in {
      val resp = await(WS.url(BASE_URL + "/authenticatedAction").withQueryString("access_token" -> noGroupUser1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
    }

    "action with custom authorization - forbid without token" in {
      val resp = await(WS.url(BASE_URL + "/customAuthorizationAction").get())
      withClue("Response body: " + resp.body) { resp.status mustBe (UNAUTHORIZED) }
    }

    "action with custom authorization - forbid to user that has incorrect first name" in {
      val resp = await(WS.url(BASE_URL + "/customAuthorizationAction").withQueryString("access_token" -> noGroupUser1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (FORBIDDEN) }
    }

    "action with custom authorization - access with user that has correct first name" in {
      val resp = await(WS.url(BASE_URL + "/customAuthorizationAction").withQueryString("access_token" -> noGroupUser2Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
    }

    "action that requires permission - forbid without token" in {
      val resp = await(WS.url(BASE_URL + "/permissionAuthorizationAction").get())
      withClue("Response body: " + resp.body) { resp.status mustBe (UNAUTHORIZED) }
    }

    "action that requires permission - forbid to user that doesn't have permissions" in {
      val resp = await(WS.url(BASE_URL + "/permissionAuthorizationAction").withQueryString("access_token" -> noGroupUser1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (FORBIDDEN) }
    }

    "action that requires permission - forbid to user that has incorrect permission" in {
      val resp = await(WS.url(BASE_URL + "/permissionAuthorizationAction").withQueryString("access_token" -> anotherGroupUser1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (FORBIDDEN) }
    }

    "action that requires permission - access with user that has correct permission via group membership" in {
      val resp = await(WS.url(BASE_URL + "/permissionAuthorizationAction").withQueryString("access_token" -> sampleGroupUser1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
    }

    "action that requires permission - access with user that has correct permission via subgroup membership" in {
      val resp = await(WS.url(BASE_URL + "/permissionAuthorizationAction").withQueryString("access_token" -> sampleSubgroupUser1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
    }

    "action that requires permission - access by admin" in {
      val resp = await(WS.url(BASE_URL + "/permissionAuthorizationAction").withQueryString("access_token" -> admin1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
    }
  }
}
