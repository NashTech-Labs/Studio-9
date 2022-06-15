package baile.services.common

case class EntityUpdateFailedException[T](
  id: String,
  entityInfo: String
) extends RuntimeException(s"Update returned no result for $entityInfo with id: '$id'.")

object EntityUpdateFailedException {

  def apply[T](
    id: String,
    entity: T
  ): EntityUpdateFailedException[T] = new EntityUpdateFailedException(id, entity.toString)

  def apply[T](
    id: String,
    entityClass: Class[T]
  ): EntityUpdateFailedException[T] = new EntityUpdateFailedException[T](id, entityClass.getName)

}
