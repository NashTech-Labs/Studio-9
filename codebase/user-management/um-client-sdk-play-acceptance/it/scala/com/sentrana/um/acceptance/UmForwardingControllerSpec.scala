package com.sentrana.um.acceptance

import com.sentrana.um.client.play.UmForwardingController
import org.scalatest.{DoNotDiscover, BeforeAndAfterAll}
import org.scalatestplus.play.{OneServerPerSuite, PlaySpec}
import play.api.Configuration
import play.api.http.Status
import play.api.libs.ws.WSResponse
import play.api.test.Helpers._

import scala.concurrent.Future

/**
  * Created by Alexander on 10.05.2016.
  */
class UmForwardingControllerSpec extends PlaySpec
  with OneServerPerSuite with BeforeAndAfterAll with WithUmClient {
  private lazy val cfg = app.injector.instanceOf(classOf[Configuration])

  private var token1: String = _

  private lazy val umForwardingController = new UmForwardingController(cfg, umClient, wsClient)

  "UmForwardingControllerSpec" must {
    "sign in user with correct password" in {
      SampleData.init(cfg)

      val resp = await(wsClient.url(s"http://localhost:$port/acceptance/token").
        withHeaders(FORM_URL_ENCODED_HEADER).
        post(buildRawRequest(SampleData.users.admin1.username, SampleData.users.admin1.passwordPlain)))

      token1 =(resp.json \ "access_token").asOpt[String].value
      (resp.json \ "expires_in").asOpt[Int] must not be (empty)
    }

    "don't sign in user with incorrect password" in {
      val resp = wsClient.url(s"http://localhost:$port/acceptance/token").
        withHeaders(FORM_URL_ENCODED_HEADER).
        post(buildRawRequest(SampleData.users.admin1.username, "fakePwd"))
      validateBadSignInRequest(resp)
    }

    "don't sign in user with incorrect username" in {
      val resp = wsClient.url(s"http://localhost:$port/acceptance/token").
        withHeaders(FORM_URL_ENCODED_HEADER).
        post(buildRawRequest("fakeUser", SampleData.users.admin1.passwordPlain))
      validateBadSignInRequest(resp)
    }

    "invalidate token" in {
      val resp = await(wsClient.url(s"http://localhost:$port/acceptance/token/${token1}").
        withHeaders(FORM_URL_ENCODED_HEADER).delete())

      resp.status mustBe Status.OK
      (resp.json \ "message").as[String] mustBe s"Token ${token1} was invalidated"
    }
  }

  private def validateBadSignInRequest(resp: Future[WSResponse]): Unit = {
    val wsResponse = await(resp)
    wsResponse.status mustBe Status.BAD_REQUEST

    (wsResponse.json \ "error").as[String] mustBe "invalid_grant"
    (wsResponse.json \ "error_description").as[String] mustBe "Invalid credentials"
  }

  private def buildRawRequest(username: String, password: String): Array[Byte] = {
    s"""username=${username}&password=${password}&grant_type=password""".getBytes
  }

  private val FORM_URL_ENCODED_HEADER = ("content-type" -> "application/x-www-form-urlencoded")
}
