package com.sentrana.umserver.controllers

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.dtos.{ CreateDataFilterInfoRequest, UpdateDataFilterInfoRequest }
import com.sentrana.umserver.services._
import com.sentrana.umserver.shared.dtos.DataFilterInfo
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.json.{ JsValue, Json, Reads }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Alexander on 26.05.2016.
 */
@Singleton
class DataFilterInfoController @Inject() (
    dataFilterInfoService:         DataFilterInfoService,
    dataFilterInfoQueryService:    DataFilterInfoQueryService,
    val securedControllerServices: SecuredControllerServices
) extends EntityCrudController[CreateDataFilterInfoRequest, UpdateDataFilterInfoRequest, DataFilterInfo] {
  import com.sentrana.umserver.JsonFormats._

  override def entityCrudService: EntityCommandService[CreateDataFilterInfoRequest, UpdateDataFilterInfoRequest, DataFilterInfo] = dataFilterInfoService

  override def entityQueryService = dataFilterInfoQueryService

  override protected def entityToDtoJson(entity: DataFilterInfo): Future[JsValue] = Future.successful(Json.toJson(entity))

  override protected def permissionPrefix: String = "FILTERS"

  override protected implicit def updReqReads: Reads[UpdateDataFilterInfoRequest] = UpdateDataFilterInfoRequest.reads

  override protected implicit def createReqReads: Reads[CreateDataFilterInfoRequest] = CreateDataFilterInfoRequest.reads

  override protected def entityName: String = "DataFilterInfo"

  def getValidValues(filterId: String) = SecuredAction(RequirePermission(permissionPrefix + "_GET_DETAILS")).async { req =>
    dataFilterInfoQueryService.getValidValues(filterId).map { validValues =>
      Ok(Json.toJson(validValues))
    }
  }

  private val log = LoggerFactory.getLogger(classOf[DataFilterInfoController])
}
