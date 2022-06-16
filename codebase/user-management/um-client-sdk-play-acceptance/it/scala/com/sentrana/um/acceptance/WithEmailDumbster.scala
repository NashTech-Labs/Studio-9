package com.sentrana.um.acceptance

import com.dumbster.smtp.SimpleSmtpServer
import org.scalatest.{Suite, BeforeAndAfterAll}
import org.slf4j.LoggerFactory
import play.api.Application

/**
  * Created by Paul Lysak on 06.09.16.
  */
trait WithEmailDumbster extends BeforeAndAfterAll {
  this: Suite =>

  private lazy val smtpPort = System.getProperty("play.mailer.port", "25").toInt

  private var _dumbster: SimpleSmtpServer = _

  protected def dumbster = _dumbster

  override def beforeAll() = {
    super.beforeAll()
    log.info(s"Starting dumbster SMTP server on port $smtpPort")
    _dumbster = SimpleSmtpServer.start(smtpPort)
    log.info(s"Started dumbster SMTP server on port $smtpPort")
  }

  override def afterAll() = {
    super.afterAll()
    log.info(s"Stopping dumbster SMTP server")
    _dumbster.stop()
  }

  private val log = LoggerFactory.getLogger(classOf[WithEmailDumbster])

}
