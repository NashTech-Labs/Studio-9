package com.sentrana.um.client.play

import javax.inject.{ Inject, Singleton }

import play.api.Configuration
import play.api.libs.ws.WSClient
import play.api.mvc._

/**
 * Created by Alexander on 09.05.2016.
 */
@Singleton
class UmForwardingController @Inject() (cfg: Configuration, umClient: UmClient, wsClient: WSClient) extends Controller {

  def signIn = Action.async(parse.raw) { request =>
    umClient.forwardToUmServer(request, "/token")
  }

  def signOut(token: String) = Action.async(parse.raw) { request =>
    umClient.forwardToUmServer(request, s"/token/${token}")
  }
}
