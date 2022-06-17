package com.sentrana.umserver.controllers

import com.sentrana.umserver.controllers.util.{ JsonObjectParser, QueryParamsExtractor }
import com.sentrana.umserver.services.{ EntityMTCommandService, EntityMTQueryService }
import com.sentrana.umserver.shared.dtos.enums.SortOrder
import com.sentrana.umserver.shared.dtos.{ GenericResponse, SeqPartContainer, WithId }
import org.slf4j.LoggerFactory
import play.api.libs.json.{ JsValue, Json, Reads }

import scala.async.Async
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Multi-tenant verion of EntityCrudController - for entities that are tightly bound to some organization
 *
 * Created by Paul Lysak on 28.04.16.
 */
trait EntityMTCrudController[CREATE_REQ, UPD_REQ, ENTITY <: WithId]
    extends ServerSecuredController
    with JsonObjectParser
    with QueryParamsExtractor {

  import com.sentrana.umserver.JsonFormats.seqPartContainerWrites

  def entityCrudService: EntityMTCommandService[CREATE_REQ, UPD_REQ, ENTITY]

  def entityQueryService: EntityMTQueryService[ENTITY]

  protected implicit def createReqReads: Reads[CREATE_REQ]

  protected implicit def updReqReads: Reads[UPD_REQ]

  protected def permissionPrefix: String

  protected def entityToDtoJson(entity: ENTITY): JsValue

  protected def entityToDetailDtoJson(entity: ENTITY, queryString: Map[String, Seq[String]]): Future[JsValue] = Future.successful(entityToDtoJson(entity))

  protected def entityName: String

  protected lazy val EntityName = entityName.headOption.map(_.toUpper).getOrElse("") + entityName.tail

  def create(orgId: String) = SecuredAction(RequirePermission(permissionPrefix + "_CREATE", Some(orgId))).async(parseObj[CREATE_REQ]()) { req =>
    log.info(s"Create $entityName: $req")
    Async.async {
      val entity = Async.await(entityCrudService.create(orgId, req.body))
      Ok(Async.await(entityToDetailDtoJson(entity, req.queryString)))
    }
  }

  def get(orgId: String, id: String) = SecuredAction(RequirePermission(permissionPrefix + "_GET_DETAILS", Some(orgId))).async { req =>
    log.debug(s"Get $entityName $id")
    Async.async {
      Async.await(entityQueryService.get(orgId, id)) match {
        case None         => GenericResponse(s"$EntityName not found", Some(id)).notFound
        case Some(entity) => Ok(Async.await(entityToDetailDtoJson(entity, req.queryString)))
      }
    }
  }

  def find(orgScopeId: String) = SecuredAction(RequirePermission(permissionPrefix + "_SEARCH", Some(orgScopeId))).async { req =>

    def explodeTerm(term: String): (String, SortOrder) = term.head match {
      case '-' => (term.tail, SortOrder.DESC)
      case _   => (term, SortOrder.ASC)
    }

    val (limit, offset) = getLimitOffset(req)
    val sortParams = req.queryString.get("order").fold(Map.empty[String, SortOrder]){ params =>
      params.flatMap(_.split(",").map(explodeTerm)).toMap
    }
    val criteria = req.queryString.filterKeys(_ != "order")
    for {
      entities <- entityQueryService.find(orgScopeId, criteria, sortParams, offset = offset, limit = limit)
      total <- entityQueryService.count(orgScopeId, criteria)
      entityJsons = entities.map(entityToDtoJson)
    } yield Ok(Json.toJson(SeqPartContainer(entityJsons, offset.toLong, total)))
  }

  def update(orgId: String, id: String) = SecuredAction(RequirePermission(permissionPrefix + "_UPDATE", Some(orgId))).async(parseObj[UPD_REQ]()) { req =>
    log.info(s"Update $entityName $id: ${req.body}")
    Async.async {
      val entity = Async.await(entityCrudService.update(orgId, id, req.body))
      Ok(Async.await(entityToDetailDtoJson(entity, req.queryString)))
    }
  }

  def delete(orgId: String, id: String) = SecuredAction(RequirePermission(permissionPrefix + "_DELETE", Some(orgId))).async { req =>
    log.info(s"Delete $entityName $id")
    for (_ <- entityCrudService.delete(orgId, id))
      yield GenericResponse(s"$EntityName deleted", Some(id)).ok
  }

  private val log = LoggerFactory.getLogger(classOf[EntityMTCrudController[CREATE_REQ, UPD_REQ, ENTITY]])
}
