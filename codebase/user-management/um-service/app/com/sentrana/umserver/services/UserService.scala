package com.sentrana.umserver.services

import java.time.{ Clock, ZonedDateTime }
import java.util.UUID

import com.sentrana.umserver.UmSettings
import com.sentrana.umserver.dtos.{ CreateUserRequest, UserDeactivationRequest }
import com.sentrana.umserver.entities._
import com.sentrana.umserver.exceptions._
import com.sentrana.umserver.shared.dtos._
import com.sentrana.umserver.shared.dtos.enums.{ PasswordResetStatus, UserStatus }
import com.sentrana.umserver.utils.PasswordHash
import javax.inject.{ Inject, Singleton }
import org.slf4j.LoggerFactory
import play.api.cache.CacheApi
import play.api.libs.mailer.{ Email, MailerClient }

import scala.async.Async
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Paul Lysak on 12.04.16.
 */
@Singleton
class UserService @Inject() (
  clock:                       Clock,
  cache:                       CacheApi,
  val umSettings:              UmSettings,
  val mailerClient:            MailerClient,
  implicit val mongoDbService: MongoDbService,
  val qService:                UserQueryService,
  groupQueryService:           UserGroupQueryService,
  val orgQueryService:         OrganizationQueryService,
  emailTemplatesSupport:       EmailTemplatesSupport,
  appInfoQueryService:         ApplicationInfoQueryService,
  val configuration:           play.api.Configuration
)
    extends EntityMTCommandService[CreateUserRequest, UpdateUserAdminRequest, UserEntity] with PasswordValidation {

  override protected implicit val mongoEntityFormat: MongoEntityFormat[UserEntity] = MongoFormats.userEntityMongoFormat
  import UserService._
  import MongoFormats.{ passwordResetMongoFormat, userPasswordHistoryMongoFormat }

  override def create(orgId: String, req: CreateUserRequest): Future[UserEntity] = {
    Async.async {
      Async.await(validateGroups(orgId, req.groupIds))
      val userId = mongoDbService.generateId

      val now = ZonedDateTime.now(clock)
      val username = Async.await(validateUsername(orgId, userId, req.username))
      val userEmail = Async.await(validateEmail(userId, req.email))

      val entity = UserEntity(
        id                  = userId,
        username            = username,
        email               = userEmail,
        password            = generatePasswordHash(req.password),
        firstName           = req.firstName,
        lastName            = req.lastName,
        status              = req.status.getOrElse(UserStatus.ACTIVE),
        activationCode      = req.activationCode,
        created             = now,
        updated             = now,
        groupIds            = req.groupIds,
        organizationId      = orgId,
        dataFilterInstances = req.dataFilterInstances.toSet.flatten,
        externalId          = req.externalId
      )
      val user = Async.await(mongoDbService.save(entity))
      user
    }
  }

  override def update(orgId: String, userId: String, req: UpdateUserAdminRequest): Future[UserEntity] = {
    //TODO validate userName for uniqueness. Also userName and email should not be empty
    Async.async {
      req.groupIds match {
        case Some(groupIds) => Async.await(validateGroups(orgId, groupIds))
        case _              => /* nothing to validate */
      }
      val u = Async.await(qService.getMandatory(orgId, userId))
      val username = Async.await(req.username.map(validateUsername(orgId, u.id, _)).fold(Future.successful(u.username))(name => name))
      val userEmail = Async.await(req.email.map(validateEmail(u.id, _)).fold(Future.successful(u.email))(email => email))

      val uUpd = u.copy(
        username            = username,
        firstName           = req.firstName.getOrElse(u.firstName),
        lastName            = req.lastName.getOrElse(u.lastName),
        email               = userEmail,
        password            = req.password.map(generatePasswordHash).getOrElse(u.password),
        groupIds            = req.groupIds.getOrElse(u.groupIds),
        updated             = ZonedDateTime.now(clock),
        dataFilterInstances = req.dataFilterInstances.getOrElse(u.dataFilterInstances),
        externalId          = req.externalId.orElse(u.externalId)
      )
      Async.await(mongoDbService.update(uUpd, orgScope(orgId)))
    }
  }

  def updateUserStatus(orgId: String, userId: String, status: UserStatus): Future[UserEntity] = {
    for (
      u <- qService.getMandatory(orgId, userId);
      uUpd <- mongoDbService.update(u.copy(
        status  = status,
        updated = ZonedDateTime.now(clock)
      ), orgScope(orgId))
    ) yield uUpd
  }

  def updateUserStatusAndResetActivationCode(orgId: String, userId: String, status: UserStatus): Future[UserEntity] = {
    for (
      u <- qService.getMandatory(orgId, userId);
      uUpd <- mongoDbService.update(u.copy(
        status         = status,
        updated        = ZonedDateTime.now(clock),
        activationCode = None
      ), orgScope(orgId))
    ) yield uUpd
  }

  override def delete(orgId: String, userId: String): Future[UserEntity] = {
    for (ue <- updateUserStatus(orgId: String, userId, UserStatus.DELETED)) yield ue
  }

  def deactivateUser(orgId: String, userId: String, userDeactivationRequest: UserDeactivationRequest): Future[UserEntity] = {

    def ensureUserIsActive(userEntity: UserEntity): Future[Unit] = {
      if (userEntity.status == UserStatus.ACTIVE || userEntity.status == UserStatus.INACTIVE) {
        Future.successful(())
      }
      else {
        Future.failed(new ValidationException(s"${userEntity.username} is ${userEntity.status}"))
      }
    }

    val sendNotificationEmail = userDeactivationRequest.sendNotificationEmail.getOrElse(true)

    def sendEmail(user: UserEntity) = {
      if (sendNotificationEmail) sendDeactivationEmail(user)
      else Future.successful(())
    }

    def updateUser() = {
      if (sendNotificationEmail) updateUserStatusAndResetActivationCode(orgId, userId, UserStatus.DEACTIVATED)
      else updateUserStatus(orgId, userId, UserStatus.DEACTIVATED)
    }

    for {
      userEntity <- qService.getMandatory(orgId, userId)
      _ <- ensureUserIsActive(userEntity)
      updatedUserEntity <- updateUser()
      _ <- sendEmail(updatedUserEntity)
    } yield {
      updatedUserEntity
    }

  }

  /**
   * Activate the user
   */
  def activateUser(
    orgId:  String,
    userId: String
  ): Future[UserEntity] = Async.async {

    def ensureUserIsNotActive(userEntity: UserEntity): Future[Unit] = {
      if (userEntity.status != UserStatus.ACTIVE) Future.successful(())
      else Future.failed(
        new ValidationException(s"User ${userEntity.username} is already activated")
      )
    }

    val userEntity = Async.await(qService.getMandatory(orgId, userId))
    Async.await(ensureUserIsNotActive(userEntity))
    val updatedUserEntity = Async.await(updateUserStatus(orgId, userId, UserStatus.ACTIVE))

    updatedUserEntity
  }

  private def validateGroups(orgId: String, groupIds: Set[String]): Future[_] = {
    Async.async {
      val groups = Async.await(Future.sequence(groupIds.map(gid => groupQueryService.get(orgId, gid).map(gid -> _))))
      groups.foreach {
        case (gid, Some(group)) =>
          if (rootOrgId == orgId && group.organizationId != orgId)
            throw new ValidationException(s"Root org user can't belong to child org ${group.organizationId} group ${group.id}")
        case (gid, None) =>
          throw new ValidationException(s"Group $gid not found in current context")
      }
    }
  }

  def createUserWithEmailActivation(
    orgId: String,
    req:   CreateUserRequest,
    appId: String
  ): Future[UserEntity] = Async.async {
    val activationCode = req.activationCode match {
      case Some(code) => code
      case None       => generateActivationCode
    }
    val createUserRequest = req.copy(
      status         = Some(UserStatus.INACTIVE),
      activationCode = Some(activationCode)
    )

    val emailConfirmationUrlOpt = Async.await(appInfoQueryService.get(appId)).flatMap(_.emailConfirmationUrl)

    val createdUser = Async.await(create(orgId, createUserRequest))
    if (req.requireEmailConfirmation.getOrElse(true)) {
      Async.await(
        emailConfirmationUrlOpt match {
          case Some(emailConfirmationUrl) => sendActivationEmailForCreatedUser(emailConfirmationUrl, createdUser, req.password, activationCode)
          case None                       => sendActivationEmailForCreatedUser(umSettings.email.confirmationUrl, createdUser, req.password, activationCode)
        }
      )
    }
    createdUser
  }

  def signUp(orgId: String, signUpRequest: UserSignUpRequest, appId: String): Future[UserEntity] = {
    Async.async {
      val org = Async.await(orgQueryService.getMandatory(orgId))
      if (!org.signUpEnabled)
        throw new ValidationException(s"Self-service sign up is not allowed for organization ${org.name}")
      if (Async.await(qService.byUserName(orgId, signUpRequest.username, Option(org.id))).nonEmpty)
        throw new ValidationException(s"User ${signUpRequest.username} already exists in organization $orgId")
      if (Async.await(qService.byEmail(orgQueryService.rootOrgId, signUpRequest.email)).nonEmpty)
        throw new ValidationException(s"User with email ${signUpRequest.email} already registered")

      val requireEmailConfirmation: Boolean = signUpRequest.requireEmailConfirmation.getOrElse(true)

      val ue = Async.await(create(org.id, CreateUserRequest(
        signUpRequest.username,
        signUpRequest.email,
        signUpRequest.password,
        signUpRequest.firstName,
        signUpRequest.lastName,
        org.signUpGroupIds,
        status         = if (requireEmailConfirmation) Option(UserStatus.INACTIVE) else Option(UserStatus.ACTIVE),
        activationCode = Option(generateActivationCode)
      )))
      val emailConfirmationUrlOpt = Async.await(appInfoQueryService.get(appId)).flatMap(_.emailConfirmationUrl)

      if (requireEmailConfirmation) Async.await(
        emailConfirmationUrlOpt match {
          case Some(emailConfirmationUrl) => sendActivationEmail(emailConfirmationUrl, ue)
          case None                       => sendActivationEmail(umSettings.email.confirmationUrl, ue)
        }
      )
      ue
    }
  }

  private def sendActivationEmail(emailConfirmationUrl: String, ue: UserEntity): Future[Unit] = Future {
    log.info(s"Sending account activation email to ${ue.email}")
    val m = emailTemplatesSupport.accountActivation(emailConfirmationUrl, ue)
    send(m)
  }

  /**
   * Send activated email for user who was activated
   */
  private def sendActivateEmail(emailConfirmationUrl: String, ue: UserEntity): Future[Unit] = Future {
    log.info(s"Sending account activation email to ${ue.email}")
    val m = emailTemplatesSupport.deactivateToActivation(emailConfirmationUrl, ue)
    send(m)
  }

  /**
   * Send deactivated email for user who was deactivated
   */
  private def sendDeactivationEmail(ue: UserEntity): Future[Unit] = Future {
    log.info(s"Sending account deactivation email to ${ue.email}")
    val m = emailTemplatesSupport.accountDeactivation(ue)
    send(m)
  }

  private def sendActivationEmailForCreatedUser(
    emailConfirmationUrl: String,
    ue:                   UserEntity,
    rawPassword:          String,
    activationCode:       String
  ): Future[Unit] = Future {
    log.info(s"Sending account activation email to ${ue.email}")
    val mail = emailTemplatesSupport.accountCreation(emailConfirmationUrl, ue, rawPassword, activationCode)
    send(mail)
  }

  def reSendActivationLink(email: String, appId: Option[String]): Future[Option[UserEntity]] = Async.async {
    def filterInactiveUser(userEntityList: Seq[UserEntity]): Option[UserEntity] = {
      userEntityList.find(_.status == UserStatus.INACTIVE)
    }

    def getEmailConfirmationUrl: Future[Option[String]] = Async.async {
      val appInfoOpt: Option[ApplicationInfoEntity] = appId match {
        case Some(id) => Async.await(appInfoQueryService.get(id))
        case None     => None
      }
      for {
        appInfo <- appInfoOpt
        url <- appInfo.emailConfirmationUrl
      } yield url
    }

    val emailConfirmationUrlOpt = Async.await(getEmailConfirmationUrl)

    def sendMail(ueOpt: Option[UserEntity]): Future[Unit] = {
      ueOpt.map { ue =>
        emailConfirmationUrlOpt match {
          case Some(emailConfirmationUrl) => sendActivationEmail(emailConfirmationUrl, ue)
          case None                       => sendActivationEmail(umSettings.email.confirmationUrl, ue)
        }
      }.getOrElse(Future.successful(()))
    }

    val userEntityList: Seq[UserEntity] = Async.await(qService.byEmail(orgQueryService.rootOrgId, email))
    val ueOpt = filterInactiveUser(userEntityList)
    sendMail(ueOpt)
    ueOpt
  }

  def activate(orgId: String, userId: String, activationCode: String): Future[UserEntity] = {
    Async.async {
      val u = Async.await(qService.getMandatory(orgId, userId))
      if (!u.activationCode.contains(activationCode))
        throw new ValidationException(s"Activation code doesn't match")
      if (u.status != UserStatus.INACTIVE)
        throw new ValidationException(s"User ${u.id} in status ${u.status} couldn't be activated")
      Async.await(updateUserStatusAndResetActivationCode(orgId, userId, UserStatus.ACTIVE))
    }
  }

  private def validateUsername(orgId: String, userId: String, username: String): Future[String] = {
    val params = Map("organizationId" -> Seq(orgId), "id_not" -> Seq(userId), "username" -> Seq(username))
    qService.find(orgId, params).map { users =>
      if (users.nonEmpty) throw new ValidationException(s"User name $username is not unique") else username
    }
  }

  private def validateEmail(userId: String, userEmail: String): Future[String] = {
    val params = Map("id_not" -> Seq(userId), "email_case_insensitive" -> Seq(userEmail))
    qService.find(rootOrgId, params).map { users =>
      if (users.nonEmpty) throw new ValidationException(s"User email $userEmail is not unique") else userEmail
    }
  }

  def updatePassword(orgId: String, userId: String, updatePasswordRequest: UpdatePasswordRequest): Future[UserEntity] = {
    Async.async {
      val user = Async.await(qService.getMandatory(orgId, userId))
      Async.await(validateOldPassword(user, updatePasswordRequest.oldPassword))
      val updatedUser = Async.await(updateUserWithNewPassword(orgId, user, updatePasswordRequest.newPassword))
      log.info(s"Updating password for ${user.id}")
      send(emailTemplatesSupport.passwordUpdateNotification(updatedUser))
      log.info("Password update email sent")

      updatedUser
    }
  }

  private def updateUserWithNewPassword(orgId: String, user: UserEntity, newPassword: String): Future[UserEntity] = {
    for {
      validatedNewPassword <- validateNewPassword(user, newPassword)
      passwordHash = generatePasswordHash(validatedNewPassword)
      updatedUser <- mongoDbService.update[UserEntity](user.copy(password = passwordHash), orgScope(orgId))
      userPasswordHistoryRecord <- mongoDbService.save[UserPasswordHistory](UserPasswordHistory(mongoDbService.generateId, user.id, passwordHash, ZonedDateTime.now(clock)))
    } yield updatedUser
  }

  def resetPassword(user: UserEntity, appId: Option[String] = None): Future[PasswordReset] = {
    Async.async {
      Async.await(findActivePasswordResets(user.id).flatMap { activePasswordResets =>
        validatePasswordResets(activePasswordResets)
        Future.sequence {
          activePasswordResets.map { activePasswordReset =>
            mongoDbService.update[PasswordReset](activePasswordReset.copy(status = PasswordResetStatus.INVALIDATED), OrgScopeRoot)
          }
        }
      })
      val now = ZonedDateTime.now(clock)
      val passwordReset = PasswordReset(
        id         = mongoDbService.generateId,
        secretCode = generateSecretCode,
        status     = PasswordResetStatus.ACTIVE,
        userId     = user.id,
        email      = user.email,
        created    = now,
        updated    = now
      )

      val appInfoOpt: Option[ApplicationInfoEntity] = appId match {
        case Some(id) => Async.await(appInfoQueryService.get(id))
        case None     => None
      }

      val res = Async.await(mongoDbService.save[PasswordReset](passwordReset))

      log.info(s"Sending password reset email to ${user.email}...")
      send(emailTemplatesSupport.buildForgotPasswordEmail(user, passwordReset, appInfoOpt))
      log.info(s"Password reset email sent to ${user.email}")
      res
    }
  }

  private def validatePasswordResets(activePasswordResets: Seq[PasswordReset]): Unit = {
    if (activePasswordResets.exists(_.created.plusSeconds(umSettings.passwordReset.interval.toSeconds).isAfter(ZonedDateTime.now(clock)))) {
      throw new TooManyRequestsException("There is already another Active password reset request")
    }
  }

  def updateForgottenPassword(userId: String, secretCode: String, newPassword: String): Future[UserEntity] = {
    findActivePasswordResets(userId).flatMap { passwordResetRequests =>
      passwordResetRequests.find(isValidPasswordReset(_, secretCode)).map { passwordReset =>
        for {
          user <- qService.getMandatory(rootOrgId, userId)
          userWithUpdatedPassword <- updateUserWithNewPassword(rootOrgId, user, newPassword)
          updatePasswordReset <- mongoDbService.update[PasswordReset](passwordReset.copy(status = PasswordResetStatus.USED), OrgScopeRoot)
        } yield userWithUpdatedPassword
      }.getOrElse(throw new AuthenticationException(s"Password reset code $secretCode is not valid for user ${userId}"))
    }
  }

  private def isValidPasswordReset(passwordReset: PasswordReset, secretCode: String): Boolean = {
    validateSecretCode(passwordReset, secretCode)
    if (passwordReset.secretCode == secretCode)
      true
    else {
      cache.set(
        passwordUpdateAttemptKey(passwordReset.email),
        secretCode,
        umSettings.passwordReset.updateInterval
      )
      false
    }
  }

  private def validateSecretCode(passwordReset: PasswordReset, secretCode: String): Unit = {
    cache.get[String](passwordUpdateAttemptKey(passwordReset.email)).map { m =>
      throw new TooManyRequestsException(s"There is already an attempt to update forgotten password with a wrong secret code for the user ${passwordReset.email}")
    }
    ()
  }

  private def findActivePasswordResets(userId: String): Future[Seq[PasswordReset]] = {
    import org.mongodb.scala.model.Filters._
    mongoDbService.find[PasswordReset](
      and(
        equal("userId", userId),
        equal("status", PasswordResetStatus.ACTIVE.name)
      )
    ).toFuture().map { _.filter(isNotExpiredPasswordReset) }
  }

  private def isNotExpiredPasswordReset(passwordReset: PasswordReset): Boolean = {
    passwordReset.created.isAfter(
      ZonedDateTime.now(clock).minusMinutes(umSettings.passwordReset.linkLifetime.toMinutes)
    )
  }

  private def send(email: Email): Unit = {
    //This dirty hack with classloaders taken from http://stackoverflow.com/questions/21856211/javax-activation-unsupporteddatatypeexception-no-object-dch-for-mime-type-multi
    //prevents mail sending exception on some environments:
    //javax.activation.UnsupportedDataTypeException: no object DCH for MIME type multipart/mixed
    //TODO find out if there are more civilized means to prevent that error
    Thread.currentThread().setContextClassLoader(getClass.getClassLoader)

    val emailOn = configuration.getBoolean("emailOn").getOrElse(true)
    if (emailOn) mailerClient.send(email)
    ()
  }

  private def generateActivationCode: String = UUID.randomUUID().toString

  private def generateSecretCode: String = UUID.randomUUID().toString

  private[umserver] def generatePasswordHash(password: String): String = {
    PasswordHash.create(password).toBase64String
  }
}

object UserService {
  private val log = LoggerFactory.getLogger(classOf[UserService])

  def passwordUpdateAttemptKey(email: String): String = "forgot.password." + email
}
