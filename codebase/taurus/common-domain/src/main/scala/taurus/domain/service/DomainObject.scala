package taurus.domain.service

import java.util.UUID

/**
 * Marker trait identifying a service domain object
 */
trait DomainObject extends Serializable

trait DomainEntity[K] extends DomainObject {
  val id: K
}

trait UUIDEntity extends DomainEntity[UUID] {
  val id: UUID
}