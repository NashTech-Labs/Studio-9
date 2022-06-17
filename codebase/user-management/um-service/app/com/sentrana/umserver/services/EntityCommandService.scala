package com.sentrana.umserver.services

import com.sentrana.umserver.entities.MongoEntityFormat
import com.sentrana.umserver.exceptions.ItemNotFoundException
import com.sentrana.umserver.shared.dtos.WithId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Created by Paul Lysak on 28.04.16.
 */
trait EntityCommandService[CREATE_REQ, UPD_REQ, ENTITY <: WithId] {

  protected implicit val mongoEntityFormat: MongoEntityFormat[ENTITY]

  protected def mongoDbService: MongoDbService

  def create(req: CREATE_REQ): Future[ENTITY]

  def update(id: String, req: UPD_REQ): Future[ENTITY]

  def delete(id: String): Future[_] =
    mongoDbService.delete[ENTITY](id, OrgScopeRoot)

  protected def EntityName: String
}
