package com.sentrana.umserver

import org.scalatest.Suite
import org.scalatestplus.play.OneServerPerSuite
import play.api.test.FakeApplication

/**
  * Created by Paul Lysak on 13.04.16.
  */
trait OneServerWithMongo extends OneServerPerSuite with EmbeddedMongoConfig {
  this: Suite =>

  def additionalConfig: Map[String, _] = Map("umserver.url" -> s"http://localhost:$port")

  implicit override lazy val app = FakeApplication(additionalConfiguration = mongoConfig ++ additionalConfig)


  override lazy val port: Int = TestUtils.findFreePorts(1).head

  // TODO get this application context from configuration
  protected def baseUrl = s"http://localhost:$port/api/um-service/v0.1"
}
