package baile.services.table.util

import java.time.Instant

import baile.domain.table._
import baile.services.usermanagement.util.TestData.SampleUser

object TestData {

  val ColumnEntity = Column(
    name = "name",
    displayName = "displayName",
    dataType = ColumnDataType.Integer,
    variableType = ColumnVariableType.Categorical,
    align = ColumnAlign.Left,
    statistics = None
  )

  val TableEntity = Table(
    ownerId = SampleUser.id,
    name = "name",
    repositoryId = "repositoryId",
    databaseId = "databaseId",
    created = Instant.now(),
    updated = Instant.now(),
    status = TableStatus.Active,
    columns = Seq(ColumnEntity),
    `type` = TableType.Source,
    size = Some(0l),
    inLibrary = true,
    tableStatisticsStatus = TableStatisticsStatus.Pending,
    description = None
  )

}
