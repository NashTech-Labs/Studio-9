package baile.daocommons.exceptions

import baile.daocommons.EntityDao
import baile.daocommons.sorting.Field

case class UnknownSortingFieldException(
  field: Field,
  dao: EntityDao[_]
) extends RuntimeException(
  s"'$field' sorting field is not known to ${ dao.getClass.toString }"
)
