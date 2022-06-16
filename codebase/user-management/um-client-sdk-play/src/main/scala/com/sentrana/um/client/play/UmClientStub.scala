package com.sentrana.um.client.play

import java.util.UUID

import com.sentrana.um.client.play.exceptions.{ UmValidationException, UmAuthenticationException, UmItemNotFoundException }
import com.sentrana.umserver.shared.dtos.enums.{ DBType, UserStatus }
import com.sentrana.umserver.shared.dtos._
import org.slf4j.LoggerFactory
import play.api.mvc.{ RawBuffer, Request, Result }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

/**
 * Created by Paul Lysak on 26.04.16.
 */
class UmClientStub(stubRootOrgId: String = "sampleRootOrg") extends UmClient {
  override def signIn(username: String, password: String, orgId: Option[String] = None): Future[Option[(String, Int)]] =
    Future.successful(users.find({ case (u, p) => u.username == username && p == password }).
      map(u => (issueToken(u._1), TOKEN_LIFETIME)))

  override def signInByEmail(email: String, password: String, orgId: Option[String]): Future[Option[(String, Int)]] =
    Future.successful(users.find({ case (u, p) => u.email == email && p == password }).
      map(u => (issueToken(u._1), TOKEN_LIFETIME)))

  override def signOut(token: String): Future[Unit] = {
    tokens = tokens - token
    Future.successful({})
  }

  override def validateAccessToken(token: String, withOrgDetails: Boolean = false, withTimeZone: Option[String] = None): Future[Option[User]] = {
    val uOpt = userByToken(token)
    //TODO apply withOrgDetails and withTimeZone
    Future.successful(uOpt)
  }

  private def userByToken(token: String): Option[User] = {
    val userIdOpt = tokens.get(token)
    userIdOpt.flatMap(id => users.find(_._1.id == id)).map(_._1)
  }

  def addUser(
    username: String,
    password: String = "123456",
    permissions: Set[String] = Set(),
    organizationId: String = stubRootOrgId,
    fromRootOrg: Boolean = true,
    email: Option[String] = None,
    firstName: Option[String] = None,
    lastName: Option[String] = None,
    status: UserStatus = UserStatus.ACTIVE
  ): User = {
    val u = User(
      id = generateId,
      username = username,
      email = email.getOrElse(username + "@some.server.com"),
      firstName = firstName.getOrElse(username + "FN"),
      lastName = lastName.getOrElse(username + "LN"),
      status = status,
      userGroupIds = Set(),
      permissions = permissions,
      organizationId = organizationId,
      fromRootOrg = fromRootOrg,
      dataFilterInstances = Set()
    )
    users = (u, password) +: users
    u
  }

  def addGroup(
    name: String,
    parentGroupId: Option[String] = None,
    desc: Option[String] = None,
    organizationId: String = stubRootOrgId,
    permissions: Set[Permission] = Set(),
    forChildOrgs: Boolean = false
  ): UserGroup = {
    val userGroup = UserGroup(
      id = generateId,
      organizationId = organizationId,
      parentGroupId = parentGroupId,
      name = name,
      desc = desc,
      grantsPermissions = permissions,
      forChildOrgs = forChildOrgs,
      dataFilterInstances = Set()
    )
    groups = userGroup +: groups
    userGroup
  }

  def issueToken(u: User): String = {
    val t = generateId
    tokens = tokens + (t -> u.id)
    t
  }

  override def getRootOrg(): Future[Organization] = Future.successful(Organization(id = stubRootOrgId, name = "Root org"))

  override def getUser(userId: String, withOrgDetails: Boolean = false, withTimeZone: Option[String] = None): Future[Option[User]] = Future.successful(
    //TODO apply withOrgDetails and withTimeZone
    users.collect { case (user, password) if (user.id == userId) => user }.headOption
  )

  override def findUsers(
    username: Option[String] = None,
    email: Option[String] = None,
    emailPrefix: Option[String] = None,
    orgId: Option[String] = None,
    offset: Int = 0,
    limit: Int = 10
  ): Future[Seq[User]] = {
    Future.successful(users.map(_._1).filter(u =>
      username.forall(_ == u.username) &&
        email.forall(_ == u.email) &&
        emailPrefix.forall(u.email.startsWith(_)) &&
        orgId.forall(_ == u.organizationId)).drop(offset).take(limit))
  }

  override def getUserGroup(groupId: String): Future[Option[UserGroup]] = Future.successful(
    groups.filter(_.id == groupId).headOption
  )

  //too general method to be implemented by stub
  override def forwardToUmServer(request: Request[RawBuffer], path: String): Future[Result] = ???

  def addFilter(fieldName: String): DataFilterInfo = {
    val dfi = DataFilterInfo(
      id = generateId,
      fieldName = fieldName,
      fieldDesc = "Describe " + fieldName,
      valuesQuerySettings = Option(DataFilterInfoSettings(
        validValuesQuery = "TODO",
        dbName = "TODO",
        dbType = DBType.MONGO,
        dataType = "String"
      ))
    )
    filters = filters :+ dfi
    dfi
  }

  override def findFilters(fieldName: Option[String], offset: Int, limit: Int): Future[Seq[DataFilterInfo]] =
    Future.successful(filters.filter(dfi => fieldName.forall(_ == dfi.fieldName)).drop(offset).take(limit))

  //stub only returns filter instances defined for user. If more accurate simulation of "full-featured" UmClient needed - it should be implemented
  override def getFilterInstances(userId: String): Future[Map[String, DataFilterInstance]] = {
    val u = getUserSync(userId)

    val fieldToFilter = filters.map(f => f.id -> f.fieldName).toMap
    Future.successful(u.dataFilterInstances.map(dfi => fieldToFilter(dfi.dataFilterId) -> dfi).toMap)
  }

  /**
   * Create or replace filter instance for specific user and filter id = filterInstance.dataFilterId
   *
   * @param user
   * @param filterInstance
   * @param accessToken access token of user which performs operation
   * @return
   */
  override def setFilterInstance(user: User, filterInstance: DataFilterInstance, accessToken: String): Future[User] = {
    val u = getUserSync(user.id)
    val uc = u.copy(dataFilterInstances = u.dataFilterInstances.
      filterNot(_.dataFilterId == filterInstance.dataFilterId) + filterInstance)
    users = users.map {
      case (u, p) if u.id == user.id => (uc, p)
      case o => o
    }
    Future.successful(uc)
  }

  override def signUp(
    orgId: String,
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    sendActivationEmail: Boolean = true
  ): Future[String] = {
    val u = addUser(
      username = username,
      password = password,
      permissions = Set.empty,
      organizationId = orgId,
      fromRootOrg = (orgId == rootOrgId),
      email = Option(email),
      firstName = Option(firstName),
      lastName = Option(lastName),
      status = UserStatus.INACTIVE
    )
    Future.successful(u.id)
  }

  override def reSendActivationLink(email: String): Future[Unit] = {
    log.info(s"UmClientImpl would re-send activation link to $email here")
    Future.successful(())
  }

  override def initPasswordReset(email: String): Future[Unit] = {
    val c = generateId
    if (users.exists { case (u, _) => u.status == UserStatus.ACTIVE && u.email == email }) {
      passwordResetCodes = passwordResetCodes + (email -> c)
      Future.successful(())
    } else {
      Future.failed(new UmValidationException(s"No active user with email $email, can't init password reset"))
    }
  }

  override def completePasswordReset(email: String, secretCode: String, newPassword: String): Future[Unit] = {
    passwordResetCodes.get(email) match {
      case Some(c) if c == secretCode =>
        passwordResetCodes = passwordResetCodes - email
        users = users.map {
          case (u, p) if u.email == email => (u, newPassword)
          case u_p => u_p
        }
        Future.successful(())
      case _ => Future.failed(new UmAuthenticationException("No such email-secretCode pair"))
    }
  }

  override def updatePassword(accessToken: String, oldPassword: String, newPassword: String): Future[Unit] = {
    userByToken(accessToken).filter(u => getPassword(u.id).contains(oldPassword)) match {
      case Some(user) =>
        users = users.map {
          case (u, p) if u.id == user.id => (u, newPassword)
          case u_p => u_p
        }
        Future.successful(())
      case None =>
        Future.failed(new UmAuthenticationException(s"No user with token $accessToken and such password"))
    }
  }

  def activateUser(userId: String): Unit = {
    users = users.map {
      case (u, p) if u.id == userId => (u.copy(status = UserStatus.ACTIVE), p)
      case u_p => u_p
    }
  }

  def getPassword(userId: String): Option[String] = users.collectFirst { case (u, p) if u.id == userId => p }

  def getPasswordResetCode(email: String): Option[String] = passwordResetCodes.get(email)

  override protected def timeout: Duration = 30.seconds

  private def getUserSync(id: String) = users.find(_._1.id == id).
    getOrElse(throw new UmItemNotFoundException(s"User $id not found"))._1

  private val TOKEN_LIFETIME = 100500

  private def generateId: String = UUID.randomUUID().toString

  private var users: Seq[(User, String)] = Nil
  private var groups: Seq[UserGroup] = Nil
  private var tokens: Map[String, String] = Map.empty
  private var filters: Seq[DataFilterInfo] = Nil

  private var passwordResetCodes: Map[String, String] = Map.empty

  private val log = LoggerFactory.getLogger(classOf[UmClientStub])
}
