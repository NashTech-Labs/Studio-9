package com.sentrana.umserver.services

import com.sentrana.umserver.entities.MongoEntityFormat
import com.sentrana.umserver.exceptions.ItemNotFoundException
import com.sentrana.umserver.shared.dtos.WithId
import com.sentrana.umserver.shared.dtos.enums.SortOrder
import com.sentrana.umserver.utils.MongoQueryUtil
import org.bson.conversions.Bson

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by Paul Lysak on 16.06.16.
 */
trait EntityMTQueryService[ENTITY <: WithId] {
  protected implicit val mongoEntityFormat: MongoEntityFormat[ENTITY]

  protected def mongoDbService: MongoDbService

  protected def orgQueryService: OrganizationQueryService

  protected lazy val rootOrgId: String = orgQueryService.rootOrgId

  protected def EntityName: String

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

  protected def filterFields: Set[String] = Set("id", "organizationId")

  protected def sortFields: Set[String] = Set()

  def get(orgId: String, id: String): Future[Option[ENTITY]] = mongoDbService.get[ENTITY](id, orgScope(orgId))

  def getMandatory(orgId: String, id: String): Future[ENTITY] =
    get(orgId, id).map(_.getOrElse(throw new ItemNotFoundException(s"$EntityName with id=$id not found")))

  def find(
    orgScopeId: String,
    criteria:   Map[String, Seq[String]] = Map.empty,
    sortParams: Map[String, SortOrder]   = Map.empty,
    offset:     Int                      = 0,
    limit:      Int                      = 10
  ): Future[Seq[ENTITY]] = {
    val criteriaDoc = MongoQueryUtil.buildFilterDoc(criteria, filterFields)
    val sortDocOpt = MongoQueryUtil.buildSortDocOpt(sortParams, sortFields)
    mongoDbService.find[ENTITY](orgScope(orgScopeId).withOrgFilter(criteriaDoc), sortDocOpt, offset, limit).toFuture()
  }

  def count(
    orgScopeId: String,
    criteria:   Map[String, Seq[String]] = Map.empty
  ): Future[Long] = {
    val criteriaDoc = MongoQueryUtil.buildFilterDoc(criteria, filterFields)
    mongoDbService.count[ENTITY](orgScope(orgScopeId).withOrgFilter(criteriaDoc))
  }

  /**
   * By specific org. Differs from default find() when scope is root. find() returns entities from all orgs,
   * and byOrgId just for specific org
   *
   * @param orgId
   * @param offset
   * @param limit
   * @return
   */
  def byOrgId(orgId: String, offset: Int = 0, limit: Int = 10): Future[Seq[ENTITY]] = {
    find(orgId, Map("organizationId" -> Seq(orgId)), offset = offset, limit = limit)
  }
}

sealed trait OrgScope {
  def withOrgFilter(origCriteria: Bson): Bson = origCriteria
}

case object OrgScopeRoot extends OrgScope

case class OrgScopeSingle(scopeOrgId: String) extends OrgScope {

  override def withOrgFilter(origCriteria: Bson): Bson = {
    import org.mongodb.scala.model.Filters._
    and(origCriteria, equal("organizationId", scopeOrgId))
  }
}

case class OrgScopeSingleOrInherited(rootOrgId: String, scopeOrgId: String) extends OrgScope {
  override def withOrgFilter(origCriteria: Bson): Bson = {
    import org.mongodb.scala.model.Filters._
    and(
      origCriteria,
      or(
        equal("organizationId", scopeOrgId),
        and(equal("organizationId", rootOrgId), equal("forChildOrgs", true))
      )
    )
  }
}

