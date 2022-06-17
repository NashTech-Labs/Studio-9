package com.sentrana.umserver.services

import java.time.ZoneId
import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.entities.UserEntity
import com.sentrana.umserver.shared.dtos.User
import com.sentrana.umserver.shared.dtos.enums.UserConverterOptions

import scala.async.Async
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by Paul Lysak on 28.06.16.
 */
@Singleton
class UserConverter @Inject() (
    groupsQService:   UserGroupQueryService,
    orgsQueryService: OrganizationQueryService
) {

  def toUserDetailDto(ue: UserEntity, queryString: Map[String, Seq[String]]): Future[User] = {
    var u = toUserDto(ue)
    Async.async {

      val perms = Async.await(
        Future.sequence(ue.groupIds.map(groupsQService.getRecursivePermisions(ue.organizationId, _))).
          map(_.flatten).
          recoverWith({ case e => throw new RuntimeException(s"Failed to get permissions for user $u", e) })
      )
      u = u.copy(permissions = perms.map(_.name))
      if (queryString.keySet.contains(UserConverterOptions.withOrgDetails.toString)) {
        val org = Async.await(orgsQueryService.getMandatory(ue.organizationId))
        u = u.copy(organization = Option(org))
      }
      val tzOpt = queryString.get(UserConverterOptions.withTimeZone.toString).flatMap(_.headOption)
      u = tzOpt.fold(u)({ tz =>
        val z = ZoneId.of(tz)
        val o = u.organization.map(o =>
          o.copy(
            created = o.created.withZoneSameInstant(z),
            updated = o.updated.withZoneSameInstant(z)
          ))
        u.copy(
          organization = o,
          created      = u.created.withZoneSameInstant(z),
          updated      = u.updated.withZoneSameInstant(z)
        )
      })
      u
    }
  }

  def toUserDto(ue: UserEntity): User = {
    User(
      id                  = ue.id,
      username            = ue.username,
      email               = ue.email,
      firstName           = ue.firstName,
      lastName            = ue.lastName,
      status              = ue.status,
      created             = ue.created,
      updated             = ue.updated,
      userGroupIds        = ue.groupIds,
      organizationId      = ue.organizationId,
      permissions         = Set(),
      fromRootOrg         = ue.organizationId == orgsQueryService.rootOrgId,
      dataFilterInstances = ue.dataFilterInstances
    )
  }

}
