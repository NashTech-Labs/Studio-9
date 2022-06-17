package com.sentrana.umserver.services

import com.sentrana.umserver.entities.{PasswordReset, ApplicationInfoEntity}
import com.sentrana.umserver.shared.dtos.PasswordResetRequest
import com.sentrana.umserver.shared.dtos.enums.PasswordResetStatus
import com.sentrana.umserver.{OneAppWithMongo, WithAdminUser}
import org.scalatestplus.play.PlaySpec
import play.api.libs.mailer.Email
import play.api.test.FakeApplication

/**
  * Created by Alexander on 25.08.2016.
  *
  * TODO move this spec to unit tests, there's no need for running app in order to test it properly
  */
class EmailTemplatesSupportSpec extends PlaySpec with OneAppWithMongo with WithAdminUser {
  private val testSenderName = "testSenderName"
  private val testSenderAddress = "testEmailSupport@domain.test"

  private val userServiceSpecConfig = Map("password.min.length" -> "5",
    "password.max.length" -> "10",
    "umserver.email.sender.name" -> testSenderName,
    "umserver.email.sender.address" -> testSenderAddress,
    "umserver.password.reset.link.url" -> "http://localhost",
    "umserver.password.reset.link.lifetime" -> "30 h")

  private lazy val appWithAdditionalConfiguration: FakeApplication = app.copy(additionalConfiguration = app.additionalConfiguration ++ userServiceSpecConfig)
  private lazy val emailTemplatesSupport = appWithAdditionalConfiguration.injector.instanceOf(classOf[EmailTemplatesSupport])

  private val emailToResetPassword = "emailToResetPassword@test.test"

  private lazy val userToResetPassword = itUtils.createTestUser(userName = "userToResetPassword", email = Option(emailToResetPassword), orgId = rootOrg.id)

  "build ForgotPassword email" in {
    val passwordResetRequest = PasswordReset("",
      "secretCode",
      PasswordResetStatus.ACTIVE,
      userToResetPassword.id,
      userToResetPassword.email)

    val email: Email = emailTemplatesSupport.buildForgotPasswordEmail(userToResetPassword, passwordResetRequest, None)
    email.subject mustBe "Password Change Link"
    email.from mustBe s"$testSenderName <$testSenderAddress>"
    email.to mustBe Seq(s"${userToResetPassword.firstName} <${userToResetPassword.email}>")
    email.bodyHtml mustBe defined
  }

  "build ForgotPassword email with custom pwd reset link" in {
    val passwordResetRequest = PasswordReset("",
      "secretCode",
      PasswordResetStatus.ACTIVE,
      userToResetPassword.id,
      userToResetPassword.email)

    val appInfo = sampleAppInfo(pwdResetUrl = Option("sample_server.com"))

    val email: Email = emailTemplatesSupport.buildForgotPasswordEmail(userToResetPassword, passwordResetRequest, Option(appInfo))
    email.subject mustBe "Password Change Link"
    email.from mustBe s"$testSenderName <$testSenderAddress>"
    email.to mustBe Seq(s"${userToResetPassword.firstName} <${userToResetPassword.email}>")
    val body = email.bodyHtml.value
    body must include ("sample_server.com")
  }

  def sampleAppInfo(pwdResetUrl: Option[String] = None) =
    ApplicationInfoEntity(id = "100500",
      name = "sampleName",
      desc = None,
      url = None,
      clientSecret = "topSecret",
      passwordResetUrl = pwdResetUrl
    )
}
