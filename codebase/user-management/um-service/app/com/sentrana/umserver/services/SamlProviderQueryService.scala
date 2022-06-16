package com.sentrana.umserver.services

import javax.inject.{ Inject, Singleton }

import com.sentrana.umserver.dtos.SamlProvider
import com.sentrana.umserver.entities.{ MongoEntityFormat, MongoFormats }

import scala.concurrent.Future

/**
 * Created by Paul Lysak on 16.06.16.
 */
@Singleton
class SamlProviderQueryService @Inject() (
  val orgQueryService: OrganizationQueryService,
  val mongoDbService:  MongoDbService
)
    extends EntityMTQueryService[SamlProvider] {

  override protected implicit val mongoEntityFormat: MongoEntityFormat[SamlProvider] = MongoFormats.samlProviderMongoFormat

  def getByName(orgId: String, name: String): Future[Option[SamlProvider]] = {
    import org.mongodb.scala.model.Filters._
    mongoDbService.findSingle[SamlProvider](orgScope(orgId).withOrgFilter(equal("name", name)))
  }

  override protected def EntityName: String = "SamlProvider"
}
