package com.sentrana.umserver

import java.time.Clock
import javax.inject.Singleton

import com.google.inject.{ AbstractModule, Provides }
import com.sentrana.umserver.services.{ MongoDbService, QueryExecutor, ScalikeQueryExecutor }
import play.api.{ Configuration, Environment }

/**
 * Created by Paul Lysak on 12.04.16.
 */

class UmServerModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone())

    bind(classOf[QueryExecutor]).to(classOf[ScalikeQueryExecutor])
    ()
  }

  @Singleton
  @Provides
  def createMongoDbService(conf: Configuration): MongoDbService = {
    new MongoDbService(conf, "um")
  }
}
