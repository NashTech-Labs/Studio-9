package com.sentrana.umserver.controllers

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.JsonFormats._
import com.sentrana.umserver.dtos.{ CreateApplicationInfoRequest, UpdateApplicationInfoRequest }
import com.sentrana.umserver.entities.ApplicationInfoEntity
import com.sentrana.umserver.services._
import com.sentrana.umserver.shared.dtos.{ ClientSecretInfo, GenericResponse }
import play.api.Configuration
import play.api.libs.json.{ JsValue, Json, Reads }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Alexander on 28.04.2016.
 */
@Singleton
class ApplicationInfoController @Inject() (
  applicationInfoService:      ApplicationInfoService,
  applicationInfoQueryService: ApplicationInfoQueryService,
  //  protected val authenticationService:          AuthenticationService,
  //  protected val orgQueryService:                OrganizationQueryService,
  //  implicit protected val userGroupQueryService: UserGroupQueryService
  val securedControllerServices: SecuredControllerServices
)
    extends EntityCrudController[CreateApplicationInfoRequest, UpdateApplicationInfoRequest, ApplicationInfoEntity] {

  override def entityCrudService = applicationInfoService

  override def entityQueryService = applicationInfoQueryService

  override protected def entityToDtoJson(entity: ApplicationInfoEntity): Future[JsValue] = Future.successful(Json.toJson(entity.toApplicationInfoDto()))

  override protected def permissionPrefix: String = "APPS"

  override protected implicit def updReqReads: Reads[UpdateApplicationInfoRequest] = UpdateApplicationInfoRequest.reads

  override protected implicit def createReqReads: Reads[CreateApplicationInfoRequest] = CreateApplicationInfoRequest.reads

  override protected def entityName: String = "application"

  def regenerateClientSecret(applicationInfoId: String) = SecuredAction(RequirePermission(permissionPrefix + "_REGENERATE")).async { req =>
    for {
      updatedApplicationInfo <- applicationInfoService.regenerateClientSecret(applicationInfoId)
    } yield GenericResponse(s"$EntityName clientSecret regenerated", Some(updatedApplicationInfo.id)).ok
  }

  def getClientSecret(applicationInfoId: String) = SecuredAction(RequirePermission(permissionPrefix + "_CLIENT_SECRET_GET_DETAILS")).async { req =>
    applicationInfoQueryService.get(applicationInfoId).map(
      _.fold(GenericResponse("No such clientId").notFound)(appInfo =>
        Ok(Json.toJson(ClientSecretInfo(appInfo.id, appInfo.clientSecret))))
    )
  }
}
