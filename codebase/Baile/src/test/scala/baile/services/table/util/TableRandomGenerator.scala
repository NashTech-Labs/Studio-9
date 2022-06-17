package baile.services.table.util

import java.time.Instant
import java.util.UUID

import baile.RandomGenerators._
import baile.daocommons.WithId
import baile.domain.table.{
  Column, ColumnAlign, ColumnDataType, ColumnVariableType, Table, TableStatisticsStatus, TableStatus, TableType
}

object TableRandomGenerator {

  def randomTable(
    name: String = randomString(),
    ownerId: UUID = UUID.randomUUID(),
    columns: Seq[Column] = Seq.fill(randomInt(2, 10))(randomColumn()),
    status: TableStatus = randomOf(
      TableStatus.Saving,
      TableStatus.Active,
      TableStatus.Error,
      TableStatus.Inactive
    ),
    tableType: TableType = randomOf(
      TableType.Derived,
      TableType.Source
    ),
    inLibrary: Boolean = randomBoolean()
  ): WithId[Table] = WithId(
    Table(
      name = name,
      ownerId = ownerId,
      repositoryId = randomString(),
      databaseId = randomString(),
      columns = columns,
      status = status,
      created = Instant.now,
      updated = Instant.now,
      `type` = tableType,
      size = randomOpt(randomInt(10000000)),
      inLibrary = inLibrary,
      tableStatisticsStatus = randomOf(
        TableStatisticsStatus.Done,
        TableStatisticsStatus.Error,
        TableStatisticsStatus.Pending
      ),
      description = randomOpt(randomString())
    ),
    randomString()
  )

  def randomColumn(
    dataType: ColumnDataType = randomOf(
      ColumnDataType.Boolean,
      ColumnDataType.Integer,
      ColumnDataType.Long,
      ColumnDataType.String,
      ColumnDataType.Timestamp,
      ColumnDataType.Double
    ),
    variableType: ColumnVariableType = randomOf(
      ColumnVariableType.Continuous,
      ColumnVariableType.Categorical
    )
  ): Column =
    Column(
      name = randomString(),
      displayName = randomString(),
      dataType = dataType,
      variableType = variableType,
      align = ColumnAlign.Center,
      statistics = None
    )

}
