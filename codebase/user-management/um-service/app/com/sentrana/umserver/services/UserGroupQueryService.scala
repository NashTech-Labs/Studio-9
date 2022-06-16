package com.sentrana.umserver.services

import javax.inject.{ Singleton, Inject }

import com.sentrana.umserver.entities.{ MongoFormats, MongoEntityFormat }
import com.sentrana.umserver.shared.dtos.{ Permission, UserGroup }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by Paul Lysak on 16.06.16.
 */
@Singleton
class UserGroupQueryService @Inject() (
    val orgQueryService: OrganizationQueryService,
    val mongoDbService:  MongoDbService
) extends EntityMTQueryService[UserGroup] {

  override protected implicit val mongoEntityFormat: MongoEntityFormat[UserGroup] = MongoFormats.userGroupMongoFormat

  override protected def EntityName: String = "UserGroup"

  override protected def orgScope(scopeOrgId: String): OrgScope =
    if (scopeOrgId == rootOrgId) OrgScopeRoot else OrgScopeSingleOrInherited(rootOrgId, scopeOrgId)

  def getRecursivePermisions(orgId: String, groupId: String): Future[Set[Permission]] = {
    getMandatory(orgId: String, groupId).flatMap({ gr =>
      val superPerm: Future[Set[Permission]] = gr.parentGroupId.fold(Future.successful(Set[Permission]()))(getRecursivePermisions(orgId, _))
      superPerm.map(_ ++ gr.grantsPermissions)
    })
  }

  def getUserGroupHierarchy(orgId: String, groupId: String): Future[Set[UserGroup]] = {
    getAllParentGroups(orgId, Option(groupId), Set.empty)
  }

  private def getAllParentGroups(
    orgId:            String,
    parentGroupIdOpt: Option[String],
    parentUserGroups: Set[UserGroup]
  ): Future[Set[UserGroup]] = {
    parentGroupIdOpt.map { parentGroupId =>
      get(orgId, parentGroupId).flatMap { parentUserGroupOpt =>
        parentUserGroupOpt.fold(Future.successful(parentUserGroups))(userGroup =>
          getAllParentGroups(orgId, userGroup.parentGroupId, parentUserGroups ++ Set(userGroup)))
      }
    }.getOrElse(Future.successful(parentUserGroups))
  }

  override protected def filterFields: Set[String] = super.filterFields ++ Set("name")
}
