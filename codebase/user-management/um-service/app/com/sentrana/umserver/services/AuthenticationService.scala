package com.sentrana.umserver.services

import java.time.{ Clock, ZonedDateTime }
import java.util.UUID
import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.UmSettings
import com.sentrana.umserver.entities.{ ApplicationInfoEntity, UserEntity, UserLoginRecord }
import com.sentrana.umserver.shared.dtos.{ ApplicationInfo, User }
import com.sentrana.umserver.shared.dtos.enums.{ UserStatus, WellKnownPermissions }
import com.sentrana.umserver.utils.PasswordHash
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.cache.CacheApi
import play.cache.NamedCache

import scala.async.Async
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Created by Paul Lysak on 13.04.16.
 */
@Singleton
class AuthenticationService @Inject() (
    clock:                                   Clock,
    @NamedCache("access-token-cache") cache: CacheApi,
    config:                                  Configuration,
    userQueryService:                        UserQueryService,
    orgQueryService:                         OrganizationQueryService,
    appInfoQueryService:                     ApplicationInfoQueryService,
    umSettings:                              UmSettings,
    implicit val mongoDbService:             MongoDbService
) {
  import com.sentrana.umserver.entities.MongoFormats.userLoginRecordFormat

  /**
   * If orgIdOpt is defined then limits user search to that specific organization.
   * If orgIdOpt = Some("root") - it's considered as alias for root org, so user is searched in root org only, without child orgs
   * If orgIdOpt = None then user is searched in all orgs. If multiple orgs have user with same name,
   * then password is checked against each of them. First matching user is returned.
   *
   * !!!If few orgs have users with same name and password then it's not known which one of them will be authenticated.
   * So please specify valid orgId if possible
   *
   * If user status is not ACTIVE or users organization status is not ACTIVE then user is not authenticated.
   *
   * @param identifier username or email
   * @param password
   * @param orgIdOpt
   * @param remoteAddress
   * @return
   */
  def userSignIn(identifier: UserIdentifier, password: String, orgIdOpt: Option[String], remoteAddress: String): Future[Option[(String, Duration)]] = {
    Async.async {
      val orgIdUnaliasedOpt = orgIdOpt.map {
        case "root" => orgQueryService.rootOrgId
        case other  => other
      }

      val users = Async.await(
        identifier match {
          case UserName(userName) =>
            userQueryService.byUserName(orgQueryService.rootOrgId, userName, orgIdUnaliasedOpt)
          case UserEmail(email) =>
            userQueryService.byEmail(orgQueryService.rootOrgId, email, orgIdUnaliasedOpt)
        }
      )
      val activeUsers = Async.await(Future.sequence(users.map({ ue => orgQueryService.get(ue.organizationId).map(org => (ue, org)) }))).filter({
        case (ue, Some(org)) => ue.status == UserStatus.ACTIVE && org.isActive
        case _               => false
      }).map(_._1)
      val authenticatedUserOpt = activeUsers.find(validatePassword(_, password))

      val loginRecordF = (authenticatedUserOpt, users.headOption) match {
        case (Some(user), _) => createUserLoginRecord(user.username, Option(user.id), orgIdOpt, true, remoteAddress)
        case (_, Some(user)) => createUserLoginRecord(user.username, Option(user.id), orgIdOpt, false, remoteAddress)
        case _               => createUserLoginRecord(identifier.identifier, None, orgIdOpt, false, remoteAddress)
      }
      Async.await(loginRecordF)
      authenticatedUserOpt.map(issueToken)
    }
  }

  def validatePassword(user: UserEntity, pwd: String): Boolean = {
    if (umSettings.passwordPlaintextAllowed && user.password == pwd) {
      log.warn(s"Validated plain-text password for user ${user.id} ${user.username} because umserver.password.plaintext.allowed is true. Make sure it's disabled on prod.")
      true
    }
    else {
      try {
        PasswordHash.parse(user.password).checkPassword(pwd)
      }
      catch {
        case e: Exception =>
          log.error("Failed to check password", e)
          false
      }
    }
  }

  def applicationSignIn(clientId: String, clientSecret: String): Future[Option[(String, Duration)]] = {
    appInfoQueryService.get(clientId).map {
      _.filter(applicationInfo => clientSecret == applicationInfo.clientSecret).map(issueTokenToApplication)
    }
  }

  def issueToken(user: UserEntity): (String, Duration) = {
    val token = UUID.randomUUID().toString
    cache.set(tokenCacheKey(token), user.id, tokenLifetime)
    (token, tokenLifetime)
  }

  def issueTokenToApplication(applicationInfo: ApplicationInfoEntity): (String, Duration) = {
    val token = AuthenticationService.ClientTokenPrefix + UUID.randomUUID().toString
    cache.set(tokenCacheKey(token), applicationInfo.id, tokenLifetime)
    (token, tokenLifetime)
  }

  def validateToken(token: String): Future[Option[UserEntity]] = {
    val key = tokenCacheKey(token)
    Async.async {
      //retrieve user by token
      val ueOpt = Async.await(cache.get[String](key).
        fold(Future.successful[Option[UserEntity]](None))(userId =>
          userQueryService.get(orgQueryService.rootOrgId, userId)))
      //check if user and org are active
      ueOpt match {
        case Some(ue) if ue.status == UserStatus.ACTIVE =>
          val org = Async.await(orgQueryService.get(ue.organizationId))
          val isOrgActive = Async.await(orgQueryService.get(ue.organizationId)).map(_.isActive).getOrElse(false)
          val activeUeOpt = Some(ue).filter(_ => isOrgActive)
          //invalidate key
          if (activeUeOpt.isEmpty) cache.remove(key)
          activeUeOpt
        case _ => None
      }
    }
  }

  def validateApplicationToken(token: String): Future[Option[User]] = {
    cache.get[String](tokenCacheKey(token)).
      fold(Future.successful[Option[User]](None))(appId => appInfoQueryService.get(appId).flatMap (
        _.fold(Future.successful[Option[User]](None))(appInfo => Future.successful(Option(getDummyUser(appInfo.id, appInfo.name))))
      ))
  }

  def invalidateToken(token: String): Unit = {
    cache.remove(tokenCacheKey(token))
  }

  def getUserLoginRecords(userName: String, skip: Int = 0, limit: Int = 10): Future[Seq[UserLoginRecord]] = {
    import org.mongodb.scala.model.Filters._

    mongoDbService.find[UserLoginRecord](equal("loginUserName", userName), offset = skip, limit = limit).toFuture()
  }

  private def createUserLoginRecord(
    userName:      String,
    userId:        Option[String],
    orgId:         Option[String],
    loginAttempt:  Boolean,
    remoteAddress: String
  ): Future[UserLoginRecord] = {
    val entity = UserLoginRecord(
      id            = mongoDbService.generateId,
      loginIp       = remoteAddress,
      loginResult   = loginAttempt,
      loginUserId   = userId,
      loginUserName = userName,
      requestOrgId  = orgId,
      loginTime     = ZonedDateTime.now(clock)
    )
    mongoDbService.save[UserLoginRecord](entity)
  }

  private def tokenCacheKey(token: String) = "access.token." + token

  private def getDummyUser(applicationId: String, applicationName: String) = User(
    ApplicationInfo.DUMMY_ID,
    applicationId,
    StringUtils.EMPTY,
    applicationName,
    StringUtils.EMPTY,
    UserStatus.ACTIVE,
    organizationId      = StringUtils.EMPTY,
    userGroupIds        = Set(),
    dataFilterInstances = Set(),
    fromRootOrg         = true,
    permissions         = Set(WellKnownPermissions.SUPERUSER.name)
  )

  private val TOKEN_LIFETIME_KEY = "umserver.access.token.lifetime"
  private lazy val tokenLifetime = config.getMilliseconds(TOKEN_LIFETIME_KEY).map(_.milliseconds).
    getOrElse({
      val defaultLifetime = 24.hours
      log.warn(s"$TOKEN_LIFETIME_KEY not specified, will use default value $defaultLifetime")
      defaultLifetime
    })

  private val log = LoggerFactory.getLogger(classOf[AuthenticationService])
}

object AuthenticationService {
  val ClientTokenPrefix = "APP_CLIENT_TOKEN_"
}

sealed trait UserIdentifier {
  def identifier: String
}

case class UserName(userName: String) extends UserIdentifier {
  def identifier = userName
}

case class UserEmail(email: String) extends UserIdentifier {
  def identifier = email
}

