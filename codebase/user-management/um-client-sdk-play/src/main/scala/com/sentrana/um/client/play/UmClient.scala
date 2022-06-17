package com.sentrana.um.client.play

import com.sentrana.um.client.play.exceptions.{ UmItemNotFoundException }
import com.sentrana.umserver.shared.dtos._
import play.api.mvc.{ RawBuffer, Request, Result }

import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * Created by Paul Lysak on 26.04.16.
 */
trait UmClient {

  def signIn(username: String, password: String, orgId: Option[String] = None): Future[Option[(String, Int)]]

  def signInByEmail(email: String, password: String, orgId: Option[String] = None): Future[Option[(String, Int)]]

  def validateAccessToken(token: String, withOrgDetails: Boolean = false, withTimeZone: Option[String] = None): Future[Option[User]]

  def getRootOrg(): Future[Organization]

  def getUser(userId: String, withOrgDetails: Boolean = false, withTimeZone: Option[String] = None): Future[Option[User]]

  def getUserMandatory(userId: String): Future[User] = getUser(userId).map(_.getOrElse(throw new UmItemNotFoundException(s"User with id $userId not found")))

  def findUsers(
    username: Option[String] = None,
    email: Option[String] = None,
    emailPrefix: Option[String] = None,
    orgId: Option[String] = None,
    offset: Int = 0,
    limit: Int = 10
  ): Future[Seq[User]]

  def getUserGroup(groupId: String): Future[Option[UserGroup]]

  def signOut(token: String): Future[Unit]

  def forwardToUmServer(request: Request[RawBuffer], path: String): Future[Result]

  def findFilters(
    fieldName: Option[String] = None,
    offset: Int = 0,
    limit: Int = 10
  ): Future[Seq[DataFilterInfo]]

  def getFilterInstances(userId: String): Future[Map[String, DataFilterInstance]]

  /**
   * Create or replace filter instance for specific user and filter id = filterInstance.dataFilterId
   *
   * @param user
   * @param filterInstance
   * @param accessToken access token of user which performs operation
   * @return
   */
  def setFilterInstance(user: User, filterInstance: DataFilterInstance, accessToken: String): Future[User]

  def signUp(
    orgId: String,
    username: String,
    email: String,
    password: String,
    firstName: String,
    lastName: String,
    sendActivationEmail: Boolean = true
  ): Future[String]

  def reSendActivationLink(email: String): Future[Unit]

  /**
   * Initiate password reset. Doesn't really removes the password, but generates secret code that can be used for
   * password rest and sends it to user's email.
   *
   * @param email
   *
   * @return
   */
  def initPasswordReset(email: String): Future[Unit]

  def completePasswordReset(email: String, secretCode: String, newPassword: String): Future[Unit]

  def updatePassword(accessToken: String, oldPassword: String, newPassword: String): Future[Unit]

  lazy val rootOrgId = Await.result(getRootOrg(), timeout).id

  protected def timeout: Duration
}
