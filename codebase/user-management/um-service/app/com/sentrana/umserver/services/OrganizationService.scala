package com.sentrana.umserver.services

import java.time.{ Clock, ZonedDateTime }
import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.dtos.{ CreateOrganizationRequest, UpdateOrganizationRequest }
import com.sentrana.umserver.entities.MongoFormats
import com.sentrana.umserver.exceptions.ValidationException
import com.sentrana.umserver.shared.dtos.Organization
import com.sentrana.umserver.shared.dtos.enums.OrganizationStatus
import org.slf4j.LoggerFactory

import scala.async.Async
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Paul Lysak on 28.04.16.
 */
@Singleton
class OrganizationService @Inject() (
  clock:                       Clock,
  qService:                    OrganizationQueryService,
  implicit val mongoDbService: MongoDbService
)
    extends EntityCommandService[CreateOrganizationRequest, UpdateOrganizationRequest, Organization] {

  override protected implicit val mongoEntityFormat = MongoFormats.organizationMongoFormat

  override def create(req: CreateOrganizationRequest): Future[Organization] = {
    val now = ZonedDateTime.now(clock)
    val orgId = mongoDbService.generateId

    validateOrgName(orgId, req.name).flatMap { orgName =>
      val entity = Organization(
        id                   = orgId,
        name                 = orgName,
        desc                 = req.desc,
        parentOrganizationId = Option(req.parentOrganizationId),
        status               = OrganizationStatus.ACTIVE,
        applicationIds       = req.applicationIds.getOrElse(Set()),
        created              = now,
        updated              = now,
        dataFilterInstances  = req.dataFilterInstances.getOrElse(Set()),
        signUpEnabled        = req.signUpEnabled.getOrElse(false),
        signUpGroupIds       = req.signUpGroupIds.getOrElse(Set())
      )
      mongoDbService.save(entity)
    }
  }

  override def update(id: String, req: UpdateOrganizationRequest): Future[Organization] = {
    Async.async {
      val org = Async.await(qService.getMandatory(id))
      val orgName = Async.await(req.name.map(validateOrgName(id, _)).fold(Future.successful(org.name))(orgName => orgName))

      Async.await(mongoDbService.update(org.copy(
        name                = orgName,
        desc                = req.desc.orElse(org.desc),
        applicationIds      = req.applicationIds.getOrElse(org.applicationIds),
        updated             = ZonedDateTime.now(clock),
        dataFilterInstances = req.dataFilterInstances.getOrElse(org.dataFilterInstances),
        signUpEnabled       = req.signUpEnabled.getOrElse(org.signUpEnabled),
        signUpGroupIds      = req.signUpGroupIds.getOrElse(org.signUpGroupIds)
      ), OrgScopeRoot))
    }
  }

  override def delete(id: String): Future[_] = {
    Async.async {
      val org = Async.await(qService.getMandatory(id))
      if (org.parentOrganizationId.isEmpty)
        throw new ValidationException(s"Root organization $id can't be deleted")
      Async.await(mongoDbService.update(org.copy(
        status  = OrganizationStatus.DELETED,
        updated = ZonedDateTime.now(clock)
      ), OrgScopeRoot))
    }
  }

  /**
   * Make disabled or deleted organization active again
   *
   * @param id
   * @return
   */
  def enable(id: String): Future[Option[Organization]] = {
    Async.async {
      Async.await(qService.get(id)) match {
        case Some(org) =>
          val updOrg = Async.await(mongoDbService.update(org.copy(status = OrganizationStatus.ACTIVE), OrgScopeRoot))
          Option(updOrg)
        case None => None
      }
    }
  }

  override protected def EntityName: String = "Organization"

  private def validateOrgName(orgId: String, orgName: String): Future[String] = {
    val params = Map("status_not" -> Seq(OrganizationStatus.DELETED.toString), "name" -> Seq(orgName), "id_not" -> Seq(orgId))
    qService.find(params).map { orgs =>
      if (!orgs.isEmpty) throw new ValidationException(s"Non unique organization's name ${orgName}") else orgName
    }
  }

  private val log = LoggerFactory.getLogger(classOf[OrganizationService])
}
