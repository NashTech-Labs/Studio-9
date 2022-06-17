package com.sentrana.umserver

import org.scalatest.Suite
import org.scalatestplus.play.OneAppPerSuite
import play.api.test.FakeApplication

/**
  * Created by Paul Lysak on 13.04.16.
  */
trait OneAppWithMongo extends OneAppPerSuite with EmbeddedMongoConfig {
  this: Suite =>

  protected def additionalConfig: Map[String, _] = mongoConfig

  implicit override lazy val app = FakeApplication(additionalConfiguration = additionalConfig)
}
