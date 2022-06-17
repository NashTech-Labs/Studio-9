package com.sentrana.umserver

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.exceptions.ConfigurationException
import play.api.Configuration
import scala.concurrent.duration._

/**
 * Created by Paul Lysak on 19.05.16.
 */
@Singleton
class UmSettings @Inject() (cfg: Configuration) {
  private lazy val umserver = cfg.getConfig("umserver").getOrElse(Configuration.empty)

  lazy val contextPath = cfg.getString("play.http.context").getOrElse("/api/um-service/v0.1")

  lazy val host = umserver.getString("host").getOrElse("http://localhost:9000")

  lazy val urlWithPath = host + contextPath

  lazy val passwordPlaintextAllowed = umserver.getBoolean("password.plaintext.allowed").getOrElse(false)

  lazy val swaggerEnabled = umserver.getBoolean("swagger.enabled").getOrElse(false)

  lazy val samlAssertionLifetime = umserver.getMilliseconds("saml.assertion.lifetime").map(_.milliseconds).getOrElse(5.minutes)

  object email {
    lazy val confirmationUrl = umserver.getString("email.confirmationUrl").getOrElse("http//localhost:8000/emailConfirmation")

    lazy val senderName = getStringProperty(umserver, "email.sender.name")

    lazy val senderAddress = getStringProperty(umserver, "email.sender.address")
  }

  object passwordValidation {
    lazy val minLength: Int = umserver.getInt("password.validation.min.length").getOrElse(10)

    lazy val maxLength: Int = umserver.getInt("password.validation.max.length").getOrElse(128)
  }

  object passwordReset {
    private lazy val pwdReset = umserver.getConfig("password.reset").getOrElse(Configuration.empty)

    lazy val linkUrl = getStringProperty(pwdReset, "link.url")

    lazy val linkLifetime = pwdReset.getMilliseconds("link.lifetime").map(_.milliseconds).getOrElse(24.hours)

    lazy val interval = pwdReset.getMilliseconds("interval").map(_.milliseconds).getOrElse(5.minutes)

    lazy val updateInterval = pwdReset.getMilliseconds("update.interval").map(_.milliseconds).getOrElse(1.minute)
  }

  private def getStringProperty(config: Configuration, propertyName: String): String = {
    config.getString(propertyName).getOrElse(throw new ConfigurationException(s"${propertyName} is not defined"))
  }
}
