package com.sentrana.um.acceptance

import org.scalatest.DoNotDiscover
import org.scalatestplus.play.{PlaySpec, OneServerPerSuite}
import play.api.Configuration
import play.api.libs.ws.WS

import play.api.test.Helpers._

/**
  * Created by Paul Lysak on 02.05.16.
  */
class MultiTenancySpec extends PlaySpec with OneServerPerSuite with WithUmClient {
  private val IT_PREFIX = "integrationTest_"

  private lazy val cfg = app.injector.instanceOf(classOf[Configuration])

  private lazy val admin1Token = getToken(SampleData.users.admin1)
  private lazy val org1user1Token = getToken(SampleData.users.org1User1)
  private lazy val org1admin1Token = getToken(SampleData.users.org1Admin1)
  private lazy val org2admin1Token = getToken(SampleData.users.org2Admin1)

  private lazy val BASE_URL = s"http://localhost:$port/acceptance"

  "MultiTenantSampleController.orgPathAction" must {
    "root org - deny access without access token" in {
      SampleData.init(cfg)
      initSampleData()

      val resp = await(WS.url(BASE_URL + "/orgs/" + SampleData.orgs.root.id + "/orgPathAction").get())
      withClue("Response body: " + resp.body) { resp.status mustBe (UNAUTHORIZED) }
    }

    "root org - deny access with invalid access token" in {
      val resp = await(WS.url(BASE_URL+ "/orgs/" + SampleData.orgs.root.id + "/orgPathAction").withQueryString("access_token" -> "some_fake_token").get())
      withClue("Response body: " + resp.body) { resp.status mustBe (UNAUTHORIZED) }
    }

    "root org - grant access to root org admin with all data" in {
      val resp = await(WS.url(BASE_URL + "/orgs/" + SampleData.orgs.root.id + "/orgPathAction").withQueryString("access_token" -> admin1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
      (resp.json \\ "data").map(_.as[String]).toSet must be(Set("root d1", "root d2", "org 1 d1", "org 2 d1"))
    }

    "root org - deny access to regular user from org1" in {
      val resp = await(WS.url(BASE_URL + "/orgs/" + SampleData.orgs.root.id + "/orgPathAction").withQueryString("access_token" -> org1user1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (FORBIDDEN) }
    }

    "root org - deny access to superuser from org1" in {
      val resp = await(WS.url(BASE_URL + "/orgs/" + SampleData.orgs.root.id + "/orgPathAction").withQueryString("access_token" -> org1admin1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (FORBIDDEN) }
    }

    "org1 - grant access to root org admin with filtered data" in {
      val resp = await(WS.url(BASE_URL + "/orgs/" + SampleData.orgs.org1.id + "/orgPathAction").withQueryString("access_token" -> admin1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
      (resp.json \\ "data").map(_.as[String]).toSet must be(Set("org 1 d1"))
    }

    "org1 - deny access to regular user from org1" in {
      val resp = await(WS.url(BASE_URL + "/orgs/" + SampleData.orgs.org1.id + "/orgPathAction").withQueryString("access_token" -> org1user1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (FORBIDDEN) }
    }

    "org1 - grant access to superuser from org1 to filtered data" in {
      val resp = await(WS.url(BASE_URL + "/orgs/" + SampleData.orgs.org1.id + "/orgPathAction").withQueryString("access_token" -> org1admin1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
      (resp.json \\ "data").map(_.as[String]).toSet must be(Set("org 1 d1"))
    }

    "org1 - deny access to superuser from org2" in {
      val resp = await(WS.url(BASE_URL + "/orgs/" + SampleData.orgs.org1.id + "/orgPathAction").withQueryString("access_token" -> org2admin1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (FORBIDDEN) }
    }
  }


  "MultiTenantSampleController.orgPathAction" must {
    "root org item - deny access without access token" in {
      val resp = await(WS.url(BASE_URL + "/customAuthorizationAction/1").get())
      withClue("Response body: " + resp.body) { resp.status mustBe (UNAUTHORIZED) }
    }

    "root org item - deny access with incorrect access token" in {
      val resp = await(WS.url(BASE_URL + "/customAuthorizationAction/1").withQueryString("access_token" -> "fake_token").get())
      withClue("Response body: " + resp.body) { resp.status mustBe (UNAUTHORIZED) }
    }

    "root org item - grant access to root org admin" in {
      val resp = await(WS.url(BASE_URL + "/customAuthorizationAction/1").withQueryString("access_token" -> admin1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
      (resp.json \ "data").as[String] must be("root d1")
    }

    "root org item - deny access to org1 admin" in {
      val resp = await(WS.url(BASE_URL + "/customAuthorizationAction/1").withQueryString("access_token" -> org1admin1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (FORBIDDEN) }
    }

    "org1 item - grant access to root org admin" in {
      val resp = await(WS.url(BASE_URL + "/customAuthorizationAction/3").withQueryString("access_token" -> admin1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
      (resp.json \ "data").as[String] must be("org 1 d1")
    }

    "org1 item - grant access to org1 admin" in {
      val resp = await(WS.url(BASE_URL + "/customAuthorizationAction/3").withQueryString("access_token" -> org1admin1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
      (resp.json \ "data").as[String] must be("org 1 d1")
    }

    "org1 item - deny access to org1 regular user" in {
      val resp = await(WS.url(BASE_URL + "/customAuthorizationAction/3").withQueryString("access_token" -> org1user1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (FORBIDDEN) }
    }

    "org2 item - deny access to org1 admin" in {
      val resp = await(WS.url(BASE_URL + "/customAuthorizationAction/4").withQueryString("access_token" -> org1admin1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (FORBIDDEN) }
    }

    "non-existent item id - not find for admin" in {
      val resp = await(WS.url(BASE_URL + "/customAuthorizationAction/100500").withQueryString("access_token" -> org1admin1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (NOT_FOUND) }
    }
  }

  private def initSampleData() = {
    MultiTenantSampleController.sampleData = Seq(
      SampleDataItem("1", "root d1", SampleData.orgs.root.id),
      SampleDataItem("2", "root d2", SampleData.orgs.root.id),
      SampleDataItem("3", "org 1 d1", SampleData.orgs.org1.id),
      SampleDataItem("4", "org 2 d1", SampleData.orgs.org2.id)
    )
  }

}
