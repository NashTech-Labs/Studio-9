package com.sentrana.um.acceptance

import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.Configuration
import play.api.libs.ws.WS
import play.api.test.Helpers._

/**
  * Created by Paul Lysak on 11.07.16.
  */
class CustomErrResponsesSpec extends PlaySpec with OneServerPerSuite with WithUmClient {
  private val IT_PREFIX = "integrationTest_"

  private lazy val cfg = app.injector.instanceOf(classOf[Configuration])

  private lazy val admin1Token = getToken(SampleData.users.admin1)
  private lazy val org1user1Token = getToken(SampleData.users.org1User1)

  override lazy val port: Int = TestUtils.findFreePorts(1).head

  private lazy val BASE_URL = s"http://localhost:$port/acceptance"

  "CustomErrResponseController" must {
    "return custom unauthenticated message" in {
      SampleData.init(cfg)

      val resp = await(WS.url(BASE_URL+ "/custom_errors").withQueryString("access_token" -> "some_fake_token").get())

      resp.body mustBe ("who are you?")
    }

    "return custom unauthorized message" in {
      val resp = await(WS.url(BASE_URL+ "/custom_errors").withQueryString("access_token" -> org1user1Token).get())
      resp.body mustBe ("please stay out")
    }

    "return success message" in {
      val resp = await(WS.url(BASE_URL+ "/custom_errors").withQueryString("access_token" -> admin1Token).get())
      resp.body mustBe ("welcome!")
    }

  }

}
