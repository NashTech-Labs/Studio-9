package com.sentrana.umserver.services

import java.time.{ Clock, ZonedDateTime }
import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.dtos.{ CreateUserGroupRequest, UpdateUserGroupRequest }
import com.sentrana.umserver.entities.{ MongoFormats, MongoEntityFormat }
import com.sentrana.umserver.exceptions.ValidationException
import com.sentrana.umserver.shared.dtos.UserGroup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Paul Lysak on 18.04.16.
 */
@Singleton
class UserGroupService @Inject() (
  clock:                       Clock,
  implicit val mongoDbService: MongoDbService,
  userQueryService:            UserQueryService,
  qService:                    UserGroupQueryService,
  val orgQueryService:         OrganizationQueryService
)
    extends EntityMTCommandService[CreateUserGroupRequest, UpdateUserGroupRequest, UserGroup] {

  override protected implicit val mongoEntityFormat: MongoEntityFormat[UserGroup] = MongoFormats.userGroupMongoFormat

  def create(orgId: String, req: CreateUserGroupRequest): Future[UserGroup] = {
    val now = ZonedDateTime.now(clock)
    val userGroupId = mongoDbService.generateId

    validateUserGroupName(orgId, userGroupId, req.name).flatMap { userGroupName =>
      val entity = UserGroup(
        id                  = userGroupId,
        organizationId      = orgId,
        parentGroupId       = req.parentGroupId,
        name                = userGroupName,
        desc                = req.desc,
        grantsPermissions   = req.grantsPermissions,
        forChildOrgs        = req.forChildOrgs,
        created             = now,
        updated             = now,
        dataFilterInstances = req.dataFilterInstances.getOrElse(Set())
      )
      mongoDbService.save(entity)
    }
  }

  def update(orgId: String, groupId: String, req: UpdateUserGroupRequest): Future[UserGroup] = {
    //TODO validate: name of the group, parentId etc
    //TODO prevent updating shared group from parent org

    for {
      g <- qService.getMandatory(orgId, groupId)

      userGroupName <- req.name.map(validateUserGroupName(orgId, groupId, _)).fold(Future.successful(g.name))(name => name)

      gUpd <- mongoDbService.update(g.copy(
        parentGroupId       = req.parentGroupId.orElse(g.parentGroupId.filterNot(_ => req.resetParentGroupId == Some(true))),
        name                = userGroupName,
        desc                = req.desc.orElse(g.desc),
        grantsPermissions   = req.grantsPermissions.getOrElse(g.grantsPermissions),
        forChildOrgs        = req.forChildOrgs.getOrElse(g.forChildOrgs),
        updated             = ZonedDateTime.now(clock),
        dataFilterInstances = req.dataFilterInstances.getOrElse(g.dataFilterInstances)
      ), orgScope(orgId))
    } yield gUpd
  }

  private def getChildren(id: String): Future[Seq[UserGroup]] = {
    import org.mongodb.scala.model.Filters._
    mongoDbService.find(equal("parentGroupId", id)).toFuture()
  }

  override def delete(orgScopeId: String, groupId: String): Future[_] = {
    for (
      children <- getChildren(groupId);
      childrenValid <- validate(children.nonEmpty, s"Group $groupId has child groups: ${children.map(_.id)}");
      members <- userQueryService.groupMembers(orgScopeId, groupId, 0, 1);
      membersValid <- validate(members.nonEmpty, s"Group $groupId has members");
      delResult <- mongoDbService.delete(groupId, orgScope(orgScopeId))
    ) yield delResult
  }

  private def validateUserGroupName(orgId: String, userGroupId: String, userGroupName: String): Future[String] = {
    val params = Map("organizationId" -> Seq(orgId), "id_not" -> Seq(userGroupId), "name" -> Seq(userGroupName))
    qService.find(orgId, params).map { userGroups =>
      if (!userGroups.isEmpty) throw new ValidationException(s"Non unique userGroup name ${userGroupName}") else userGroupName
    }
  }

  private def validate(condition: Boolean, message: String) =
    if (condition) Future.failed(new ValidationException(message))
    else Future.successful(None)
}
