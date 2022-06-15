package baile.daocommons.exceptions

import baile.daocommons.EntityDao
import baile.daocommons.filters.Filter

case class UnknownFilterException(
  filter: Filter,
  dao: EntityDao[_]
) extends RuntimeException(
s"'$filter' filter is not known to ${ dao.getClass.toString }"
)
