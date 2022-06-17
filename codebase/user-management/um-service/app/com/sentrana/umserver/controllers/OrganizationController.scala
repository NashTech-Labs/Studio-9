package com.sentrana.umserver.controllers

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.dtos.{ CreateOrganizationRequest, UpdateOrganizationRequest }
import com.sentrana.umserver.services._
import com.sentrana.umserver.shared.dtos.Organization
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.json.{ JsValue, Json, Reads }
import play.api.mvc.{ Action, AnyContent }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by Paul Lysak on 28.04.16.
 */
@Singleton
class OrganizationController @Inject() (
  //  val authenticationService: AuthenticationService,
  //  val userGroupQueryService: UserGroupQueryService,
  orgService:                    OrganizationService,
  val orgQueryService:           OrganizationQueryService,
  val securedControllerServices: SecuredControllerServices
)
    extends EntityCrudController[CreateOrganizationRequest, UpdateOrganizationRequest, Organization] {

  import com.sentrana.umserver.JsonFormats._

  override def entityCrudService = orgService

  override def entityQueryService = orgQueryService

  override protected def entityToDtoJson(entity: Organization): Future[JsValue] = Future.successful(Json.toJson(entity))

  override protected def permissionPrefix: String = "ORGS"

  override protected implicit def createReqReads: Reads[CreateOrganizationRequest] = CreateOrganizationRequest.reads

  override protected implicit def updReqReads: Reads[UpdateOrganizationRequest] = UpdateOrganizationRequest.reads

  override protected def entityName: String = "organization"

  def getRootOrg() = SecuredAction(RequirePermission(permissionPrefix + "_GET_DETAILS")).async { req =>
    log.debug(s"Get root organization")
    orgQueryService.getRootOrg().map(org => Ok(Json.toJson(org)))
  }

  //Override authorization so that regular org users could retrieve only own organization details
  override def get(id: String): Action[AnyContent] = SecuredAction(RequirePermission(permissionPrefix + "_SEARCH", Option(id))).async { req =>
    super.get(id).apply(req)
  }

  //Override authorization so that regular org users could not search orgs
  override def find() = SecuredAction(RequirePermission(permissionPrefix + "_SEARCH", rootOrgScope)).async { req =>
    super.find().apply(req)
  }

  private lazy val rootOrgScope = Option(orgQueryService.rootOrgId)

  private val log = LoggerFactory.getLogger(classOf[OrganizationController])
}
