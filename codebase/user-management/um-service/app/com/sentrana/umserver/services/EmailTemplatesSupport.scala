package com.sentrana.umserver.services

import java.net.URLEncoder

import javax.inject.{ Inject, Singleton }
import com.sentrana.umserver.UmSettings
import com.sentrana.umserver.entities.{ ApplicationInfoEntity, PasswordReset, UserEntity }
import play.api.libs.mailer.Email

/**
 * Created by Alexander on 12.08.2016.
 */
@Singleton
class EmailTemplatesSupport @Inject() (umSettings: UmSettings) {
  def buildForgotPasswordEmail(user: UserEntity, passwordResetRequest: PasswordReset, appInfo: Option[ApplicationInfoEntity]): Email = {
    val linkBaseUrl = appInfo.flatMap(_.passwordResetUrl).getOrElse(umSettings.passwordReset.linkUrl)
    val passwordResetLink = s"$linkBaseUrl?secretCode=${encodeQueryParam(passwordResetRequest.secretCode)}&" +
      s"email=${encodeQueryParam(user.email)}"

    val body = views.html.emails.PasswordResetMail(user.firstName, passwordResetLink, umSettings.passwordReset.linkLifetime.toHours).body
    val to = s"${user.firstName} <${user.email}>"

    Email(subject = "Password Change Link", from = from, to = Seq(to), bodyHtml = Option(body))
  }

  def accountCreation(emailConfirmationUrl: String, user: UserEntity, rawPassword: String, activationCode: String): Email = {
    val activationLink = s"$emailConfirmationUrl?orgId=${encodeQueryParam(user.organizationId)}&" +
      s"userId=${encodeQueryParam(user.id)}&activationCode=${encodeQueryParam(activationCode)}"

    val body = views.html.emails.AccountCreationMail(user.firstName, user.username, rawPassword, activationLink).body
    val to = s"${user.firstName} <${user.email}>"

    Email(subject = "Welcome to DeepCortex! Please activate your account.", from = from, to = Seq(to), bodyHtml = Option(body))
  }

  def accountActivation(emailConfirmationUrl: String, user: UserEntity): Email = {
    val activationLink = s"$emailConfirmationUrl?orgId=${encodeQueryParam(user.organizationId)}&" +
      s"userId=${encodeQueryParam(user.id)}&activationCode=${encodeQueryParam(user.activationCode.getOrElse(""))}"

    val body = views.html.emails.AccountActivationMail(user.firstName, user.username, activationLink).body
    val to = s"${user.firstName} <${user.email}>"

    Email(subject = "Welcome to DeepCortex! Please activate your account.", from = from, to = Seq(to), bodyHtml = Option(body))
  }

  def accountDeactivation(user: UserEntity): Email = {
    val body = views.html.emails.AccountDeactivationMail(user.firstName, user.username).body
    val to = s"${user.firstName} <${user.email}>"

    Email(subject = "Your account has been deactivated", from = from, to = Seq(to), bodyHtml = Option(body))
  }

  def deactivateToActivation(emailConfirmationUrl: String, user: UserEntity): Email = {
    val activationLink = s"$emailConfirmationUrl?orgId=${encodeQueryParam(user.organizationId)}&" +
      s"userId=${encodeQueryParam(user.id)}&activationCode=${encodeQueryParam(user.activationCode.getOrElse(""))}"

    val body = views.html.emails.AccountReActivationMail(user.firstName, user.username, activationLink).body
    val to = s"${user.firstName} <${user.email}>"

    Email(subject = "Your account is now unlocked! Please activate your account again.", from = from, to = Seq(to), bodyHtml = Option(body))
  }

  def passwordUpdateNotification(user: UserEntity): Email = {
    val body = views.html.emails.PasswordUpdateNotificationMail(user.firstName, user.username).body
    val to = s"${user.firstName} <${user.email}>"

    Email(subject = "Sentrana Account Created", from = from, to = Seq(to), bodyHtml = Option(body))
  }

  private def from = s"${umSettings.email.senderName} <${umSettings.email.senderAddress}>"

  private def encodeQueryParam(param: String): String = {
    URLEncoder.encode(param, "UTF-8")
  }
}
