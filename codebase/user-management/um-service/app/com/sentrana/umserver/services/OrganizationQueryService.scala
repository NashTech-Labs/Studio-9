package com.sentrana.umserver.services

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.entities.MongoFormats
import com.sentrana.umserver.shared.dtos.Organization
import org.slf4j.LoggerFactory

import scala.concurrent.{ Await, Future }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * Created by Paul Lysak on 17.06.16.
 */
@Singleton
class OrganizationQueryService @Inject() (val mongoDbService: MongoDbService) extends EntityQueryService[Organization] {
  override protected implicit val mongoEntityFormat = MongoFormats.organizationMongoFormat

  override protected def EntityName: String = "Organization"

  def getRootOrg(): Future[Organization] = {
    import org.mongodb.scala.model.Filters._
    mongoDbService.find(equal("parentOrganizationId", null)).toFuture().map({ orgs =>
      if (orgs.isEmpty)
        throw new RuntimeException(s"No root orgs detected - server is misconfigured")
      if (orgs.tail.nonEmpty)
        log.error(s"Multiple root orgs detected - server is misconfigures: " + orgs)
      orgs.head
    })
  }

  override protected def filterFields: Set[String] = super.filterFields ++ Seq("name", "status")

  //will be called once per service, we can afford waiting here
  //TODO make timeout configurable
  lazy val rootOrgId: String = Await.result(getRootOrg(), 30.seconds).id

  private val log = LoggerFactory.getLogger(classOf[OrganizationQueryService])
}

