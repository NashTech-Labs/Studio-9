package com.sentrana.um.acceptance

import com.sentrana.um.client.play.UmClientImpl
import org.scalatest.OptionValues
import play.api.Application
import play.api.cache.CacheApi
import play.api.libs.ws.WSClient
import play.api.test.Helpers._

/**
  * Created by Paul Lysak on 26.04.16.
  */
trait WithUmClient extends OptionValues {

  protected def app: Application

  protected lazy val umServiceUrl = System.getProperty("sentrana.um.server.url", "http://localhost:9000")
  protected lazy val wsClient = app.injector.instanceOf(classOf[WSClient])
  protected lazy val cache = app.injector.instanceOf(classOf[CacheApi])
  //TODO get url from config
  protected lazy val umClient = new UmClientImpl(umServiceUrl, SampleData.apps.defaultApp.id, SampleData.apps.defaultApp.clientSecret, cache, wsClient)

  protected def getToken(u: SampleUser) = await(umClient.signIn(u.username, u.passwordPlain, Option(u.organizationId))).value._1
}
