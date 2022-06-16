package com.sentrana.um.acceptance

import javax.inject.{ Inject, Singleton }

import com.sentrana.um.acceptance.exceptions.AcceptanceItemNotFoundException
import com.sentrana.um.client.play.{ SecuredController, UmClient }
import com.sentrana.umserver.shared.BaseSecuredController.Authorization
import play.api.Configuration
import play.api.libs.json.Json

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

/**
 * Created by Paul Lysak on 02.05.16.
 */
case class SampleDataItem(id: String, data: String, orgId: String)

object SampleDataItem {
  implicit val writes = Json.writes[SampleDataItem]
}

@Singleton
class MultiTenantSampleController @Inject() (val umClient: UmClient) extends SecuredController {

  /**
   * Default approach to multi-tenant resources protection - include scope orgId in URL.
   * That's the simplest way to detect organization id, as there's no need to support
   * multiple sources of organization id - request body, path, query params, DB entities retrieved by ID etc.
   *
   * scopeOrgId is expected to provide scope - only entities which belong to organization scopeOrgId or its sub-organizations
   * are accessable here. If new entities are created, they're expected to reside in organization scopeOrgId.
   *
   * @param scopeOrgId
   * @return
   */
  def orgPathAction(scopeOrgId: String) = SecuredAction(RequirePermission("SAMPLE_PERMISSION", Some(scopeOrgId))) { req =>
    //We know that req.user has permission SAMPLE_PERMISSION in organization scopeOrgId and its child orgs
    //Now we need to restrict available data to that scope
    val filteredData = MultiTenantSampleController.sampleData.filter(orgMatch(scopeOrgId))
    Ok(Json.toJson(filteredData))
  }

  /**
   * If application needs custom authorization rules, it can be implemented from scratch taking authenticated user
   * and request as a parameters and returning Future[Boolean].
   *
   * In this example it detects organization id from itemId and checks if current user has enough permission to work with it.
   *
   * @param itemId
   * @return
   */
  def customAuthorizationAction(itemId: String) = SecuredAction({
    case (user, request) => Future.successful {
      MultiTenantSampleController.sampleData.find(_.id == itemId).fold(throw new AcceptanceItemNotFoundException("TODO")){ item =>
        val orgMatch = user.fromRootOrg || item.orgId == user.organizationId
        orgMatch && user.hasPermission("SAMPLE_PERMISSION")
      }
    }
  }: Authorization) { req =>
    Ok(Json.toJson(MultiTenantSampleController.sampleData.find(_.id == itemId).getOrElse(throw new AcceptanceItemNotFoundException("TODO"))))
  }

  private def orgMatch(scopeOrgId: String)(d: SampleDataItem): Boolean =
    if (scopeOrgId == rootOrgId) true else d.orgId == scopeOrgId
}

object MultiTenantSampleController {
  private[acceptance] var sampleData: Seq[SampleDataItem] = Nil
}

