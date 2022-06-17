package com.sentrana.um.acceptance

import com.google.inject.{ Scopes, AbstractModule }
import com.sentrana.um.client.play.{ UmClient, UmClientImplProvider }

/**
 * Created by Paul Lysak on 26.04.16.
 */
class AcceptanceModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[UmClient]).toProvider(classOf[UmClientImplProvider]).in(Scopes.SINGLETON)
  }
}
