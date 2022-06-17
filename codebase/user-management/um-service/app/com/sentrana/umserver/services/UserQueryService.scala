package com.sentrana.umserver.services

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.entities.{ MongoEntityFormat, MongoFormats, UserEntity }
import com.sentrana.umserver.exceptions.ItemNotFoundException
import com.sentrana.umserver.shared.dtos.enums.{ SortOrder, UserStatus }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Paul Lysak on 16.06.16.
 */
@Singleton
class UserQueryService @Inject() (
    val orgQueryService: OrganizationQueryService,
    val mongoDbService:  MongoDbService
) extends EntityMTQueryService[UserEntity] {

  override protected implicit val mongoEntityFormat: MongoEntityFormat[UserEntity] = MongoFormats.userEntityMongoFormat

  override protected def EntityName: String = "User"

  def byUserName(orgScopeId: String, username: String, strictOrgId: Option[String] = None, offset: Int = 0, limit: Int = 10): Future[Seq[UserEntity]] = {
    find(orgScopeId, Map("username" -> Seq(username)) ++ strictOrgId.map(id => "organizationId" -> Seq(id)).toMap, offset = offset, limit = limit)
  }

  def byEmail(orgScopeId: String, email: String, strictOrgId: Option[String] = None, offset: Int = 0, limit: Int = 10): Future[Seq[UserEntity]] = {
    find(orgScopeId, Map("email_case_insensitive" -> Seq(email)) ++ strictOrgId.map(id => "organizationId" -> Seq(id)).toMap, offset = offset, limit = limit)
  }

  def groupMembers(orgScopeId: String, groupId: String, offset: Int = 0, limit: Int = 10): Future[Seq[UserEntity]] = {
    find(orgScopeId, Map("groupIds" -> Seq(groupId)), offset = offset, limit = limit)
  }

  def getByExternalId(orgId: String, externalId: String): Future[Option[UserEntity]] = {
    import org.mongodb.scala.model.Filters._
    mongoDbService.findSingle[UserEntity](orgScope(orgId).withOrgFilter(equal("externalId", externalId)))
  }

  override def find(orgScopeId: String, criteria: Map[String, Seq[String]], sort: Map[String, SortOrder], offset: Int, limit: Int): Future[Seq[UserEntity]] =
    super.find(orgScopeId, criteria ++ notDeletedCriterion, sort, offset, limit)

  override def count(orgScopeId: String, criteria: Map[String, Seq[String]]): Future[Long] =
    super.count(orgScopeId, criteria ++ notDeletedCriterion)

  def findActiveUser(email: String): Future[UserEntity] = {
    val users = find(orgQueryService.rootOrgId, Map("email_case_insensitive" -> Seq(email), "status" -> Seq(UserStatus.ACTIVE.name())))
    users.map { u =>
      u.headOption.getOrElse(throw new ItemNotFoundException(s"No user with email ${email} were found "))
    }
  }

  override protected val filterFields: Set[String] = super.filterFields ++ Set("status", "username", "email", "groupIds", "firstName", "lastName")

  override protected val sortFields: Set[String] = super.sortFields ++ Set("username", "email", "firstName", "lastName", "created", "updated")

  private val notDeletedCriterion = Map("status_not" -> Seq(UserStatus.DELETED.toString))
}
