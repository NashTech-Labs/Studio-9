package baile.utils

import java.util.Properties

import akka.event.LoggingAdapter
import com.typesafe.config.Config
import javax.mail.internet.{ InternetAddress, MimeMessage }
import javax.mail.{ Message, Session, Transport }

import scala.io.Source
import scala.util.Try

class MailService(
  config: Config,
  logger: LoggingAdapter
) {

  private val appUrl = config.getString("web-app-url")
  private val htmlLayoutTemplatePath = config.getString("html.layout-template")
  private val htmlTemplate = Source.fromResource(htmlLayoutTemplatePath).mkString

  private val mailerConfig = config.getConfig("mailer")
  private val port = mailerConfig.getString("port")
  private val hostname = mailerConfig.getString("hostname")
  private val username = mailerConfig.getString("username")
  private val password = mailerConfig.getString("password")
  private val senderEmail = mailerConfig.getString("sender-email")
  private val senderName = mailerConfig.getString("sender-name")
  private val protocol = mailerConfig.getString("protocol")
  private val properties = new Properties
  properties.put("mail.smtp.port", port)
  properties.setProperty("mail.transport.protocol", "smtp")
  properties.setProperty("mail.smtp.starttls.enable", "true")
  properties.setProperty("mail.host", hostname)
  properties.setProperty("mail.user", username)
  properties.setProperty("mail.password", password)
  properties.setProperty("mail.smtp.auth", "true")
  private val session: Session = Session.getDefaultInstance(properties)
  private val transport: Transport = session.getTransport(protocol)

  def sendHtmlFormattedEmail(subject: String, messageBody: String, toAddress: String, receiverName: String): Try[Unit] =
    Try {
      val message: MimeMessage = new MimeMessage(session)
      message.setFrom(new InternetAddress(senderEmail, senderName))
      val recipientAddress: InternetAddress = new InternetAddress(toAddress, receiverName)
      message.addRecipient(Message.RecipientType.TO, recipientAddress)
      message.setSubject(subject)
      message.setHeader("Content-Type", "text/plain;")
      message.setContent(
        htmlTemplate
          .replace("#HTML_BODY", messageBody)
          .replace("#APP_URL", appUrl),
        "text/html"
      )
      transport.connect(hostname, username, password)
      transport.sendMessage(message, message.getAllRecipients)
    }

}
