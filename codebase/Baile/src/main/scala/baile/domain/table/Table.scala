package baile.domain.table

import java.time.Instant
import java.util.UUID

import baile.domain.asset.Asset

case class Table(
  name: String,
  ownerId: UUID,
  repositoryId: String,
  databaseId: String,
  columns: Seq[Column],
  status: TableStatus,
  created: Instant,
  updated: Instant,
  `type`: TableType,
  size: Option[Long] = None,
  inLibrary: Boolean,
  tableStatisticsStatus: TableStatisticsStatus,
  description: Option[String]
) extends Asset[TableStatus]
