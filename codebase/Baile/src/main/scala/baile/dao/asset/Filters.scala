package baile.dao.asset

import java.util.UUID

import baile.daocommons.filters.Filter

object Filters {
  case class OwnerIdIs(userId: UUID) extends Filter
  case class NameIs(name: String) extends Filter
  case class SearchQuery(term: String) extends Filter
  case class InLibraryIs(inLibrary: Boolean) extends Filter
}
