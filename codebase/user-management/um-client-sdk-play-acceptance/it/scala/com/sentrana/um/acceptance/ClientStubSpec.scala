package com.sentrana.um.acceptance

import com.sentrana.um.client.play.{UmClient, UmClientStub}
import org.scalatest.DoNotDiscover
import org.scalatestplus.play.{PlaySpec, OneServerPerSuite}
import play.api.Mode
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WS
import play.api.test.Helpers._
import play.api.inject.bind

/**
  * Unlike other specs from acceptance module, this one doesn't require um-server to be running - it uses stub client
  *
  * Created by Paul Lysak on 26.04.16.
  */
class ClientStubSpec extends PlaySpec with OneServerPerSuite {
  private val umClientStub = new UmClientStub
  private val user1 = umClientStub.addUser("umSampleUser", permissions = Set("DO_THIS", "DO_THAT"))
  private val user1Token = umClientStub.issueToken(user1)

  override lazy val port: Int = TestUtils.findFreePorts(1).head

  override implicit lazy val app = new GuiceApplicationBuilder()
    .in(Mode.Test)
    .overrides(bind[UmClient].toInstance(umClientStub))
    .build

  private lazy val BASE_URL = s"http://localhost:$port/acceptance"

  "SecuredAction" must {
     "do not return user without token" in {
      val resp = await(WS.url(BASE_URL + "/currentUserAction").get())
      withClue("Response body: " + resp.body) { resp.status mustBe (UNAUTHORIZED) }
    }

    "recognize user by token" in {
      val resp = await(WS.url(BASE_URL + "/currentUserAction").withQueryString("access_token" -> user1Token).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
      (resp.json \ "username").as[String] must be (user1.username)
      (resp.json \ "permissions").as[Set[String]] must be(Set("DO_THIS", "DO_THAT"))
    }
  }
}
