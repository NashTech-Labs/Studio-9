package argo.domain.service

import java.util.UUID

trait ServiceMessage

case class CreateEntity(entityData: DomainObject) extends ServiceMessage
case class RetrieveEntity(entityId: UUID) extends ServiceMessage
case class UpdateEntity(entityId: UUID, entityData: DomainObject) extends ServiceMessage
case class DeleteEntity(entityId: UUID) extends ServiceMessage
case object ListEntities extends ServiceMessage