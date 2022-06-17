package com.sentrana.umserver.controllers

import com.sentrana.umserver.controllers.util.{ JsonObjectParser, QueryParamsExtractor }
import com.sentrana.umserver.services.{ EntityCommandService, EntityQueryService }
import com.sentrana.umserver.shared.dtos.enums.SortOrder
import com.sentrana.umserver.shared.dtos.{ GenericResponse, SeqPartContainer, WithId }
import org.slf4j.LoggerFactory
import play.api.libs.json.{ JsValue, Json, Reads }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Paul Lysak on 28.04.16.
 */
trait EntityCrudController[CREATE_REQ, UPD_REQ, ENTITY <: WithId]
    extends ServerSecuredController
    with JsonObjectParser
    with QueryParamsExtractor {

  import com.sentrana.umserver.JsonFormats.seqPartContainerWrites

  def entityCrudService: EntityCommandService[CREATE_REQ, UPD_REQ, ENTITY]

  def entityQueryService: EntityQueryService[ENTITY]

  protected implicit def createReqReads: Reads[CREATE_REQ]

  protected implicit def updReqReads: Reads[UPD_REQ]

  protected def permissionPrefix: String

  protected def entityToDtoJson(entity: ENTITY): Future[JsValue]

  protected def entityName: String

  protected lazy val EntityName = entityName.headOption.map(_.toUpper).getOrElse("") + entityName.tail

  def create = SecuredAction(RequirePermission(permissionPrefix + "_CREATE", rootOrgScope)).async(parseObj[CREATE_REQ]()) { req =>
    log.info(s"Create $entityName: $req")
    for (entity <- entityCrudService.create(req.body))
      yield GenericResponse(s"$EntityName created", Some(entity.id)).ok
  }

  //scope = None - users from leaf orgs should by default be able to read non-multitenant objects
  def get(id: String) = SecuredAction(RequirePermission(permissionPrefix + "_GET_DETAILS", None)).async { req =>
    log.debug(s"Get $entityName $id")
    entityQueryService.get(id).flatMap(_.fold(
      GenericResponse(s"$EntityName not found", Some(id)).notFoundF
    )({
      entityToDtoJson(_).map({ j => Ok(j) })
    }))
  }

  //scope = None - users from leaf orgs should by default be able to read non-multitenant objects
  def find() = SecuredAction(RequirePermission(permissionPrefix + "_SEARCH", None)).async { req =>

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
      entities <- entityQueryService.find(criteria, sortParams, offset = offset, limit = limit)
      total <- entityQueryService.count(req.queryString)
      dtos <- Future.sequence(entities.map(entityToDtoJson))
    } yield Ok(Json.toJson(SeqPartContainer(dtos, offset.toLong, total)))
  }

  def update(id: String) = SecuredAction(RequirePermission(permissionPrefix + "_UPDATE", rootOrgScope)).async(parseObj[UPD_REQ]()) { req =>
    log.info(s"Update $entityName $id: ${req.body}")
    for (ue <- entityCrudService.update(id, req.body))
      yield GenericResponse(s"$EntityName updated", Some(ue.id)).ok
  }

  def delete(id: String) = SecuredAction(RequirePermission(permissionPrefix + "_DELETE", rootOrgScope)).async { req =>
    log.info(s"Delete $entityName $id")
    for (_ <- entityCrudService.delete(id))
      yield GenericResponse(s"$EntityName deleted", Some(id)).ok
  }

  private lazy val rootOrgScope = Option(securedControllerServices.orgQueryService.rootOrgId)

  private val log = LoggerFactory.getLogger(classOf[EntityCrudController[CREATE_REQ, UPD_REQ, ENTITY]])
}
