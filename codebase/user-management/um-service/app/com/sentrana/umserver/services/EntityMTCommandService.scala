package com.sentrana.umserver.services

import com.sentrana.umserver.entities.MongoEntityFormat
import com.sentrana.umserver.exceptions.ItemNotFoundException
import com.sentrana.umserver.shared.dtos.WithId
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.bson.conversions.Bson

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

/**
 * Multi-tenant version of EntityCrudService
 * Created by Paul Lysak on 28.04.16.
 */
trait EntityMTCommandService[CREATE_REQ, UPD_REQ, ENTITY <: WithId] {

  protected implicit val mongoEntityFormat: MongoEntityFormat[ENTITY]

  protected def mongoDbService: MongoDbService

  def create(orgId: String, req: CREATE_REQ): Future[ENTITY]

  def update(orgId: String, id: String, req: UPD_REQ): Future[ENTITY]

  def delete(orgId: String, id: String): Future[_] =
    mongoDbService.delete[ENTITY](id, orgScope(orgId))

  protected def orgQueryService: OrganizationQueryService

  protected lazy val rootOrgId: String = orgQueryService.rootOrgId

  /**
   * Org filter for Mongo querries.
   * Assumes that organization hierarchy has just 2 levels - root and leaf.
   * If orgId is leaf one, then it's applied without changes. If it's root - no filter applied at all.
   *
   * @param scopeOrgId
   * @return
   */
  protected def orgScope(scopeOrgId: String): OrgScope =
    if (scopeOrgId == rootOrgId) OrgScopeRoot else OrgScopeSingle(scopeOrgId)

  protected def EntityName = "Item"

  protected def filterFields: Set[String] = Set("id", "organizationId")
}

