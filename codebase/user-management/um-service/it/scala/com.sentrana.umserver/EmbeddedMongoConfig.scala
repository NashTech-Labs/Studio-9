package com.sentrana.umserver

import java.net.ServerSocket

import com.github.simplyscala.{MongodProps, MongoEmbedDatabase}
import de.flapdoodle.embed.mongo.distribution.Version
import org.scalatest.{Suite, BeforeAndAfterAll}
import org.scalatestplus.play.{ServerProvider, OneServerPerSuite}
import play.api.test.FakeApplication

import scala.util.Try

/**
  * Created by Paul Lysak on 12.04.16.
  */
trait EmbeddedMongoConfig extends MongoEmbedDatabase with BeforeAndAfterAll {
  this: Suite =>

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    mongoPort = findNewPort
//    mongoPort = 12345
    mongoProps = mongoStart(port = mongoPort, version = Version.V3_2_0)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    mongoStop(mongoProps)
  }

  private var mongoPort: Int = _

  private var mongoProps: MongodProps = _

  protected def mongoConfig: Map[String,String] = {
    Map("mongodb.um.uri" -> s"mongodb://localhost:$mongoPort/test")
  }

  private def findNewPort: Int = {
    val socket = Try(new ServerSocket(0))
    socket foreach {
      _.setReuseAddress(true)
    }
    val port = socket map {
      _.getLocalPort
    }
    socket foreach {
      _.close()
    }
    port getOrElse {
      throw new IllegalStateException("Could not find a free TCP/IP port")
    }
  }
}
