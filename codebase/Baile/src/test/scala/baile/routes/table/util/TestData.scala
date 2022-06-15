package baile.routes.table.util

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.table.{ Table, TableStatisticsStatus, TableStatus, TableType }
import baile.services.usermanagement.util.TestData.SampleUser
import baile.services.table.TableService.UpdateColumnParams

object TestData {

  val DateNow = Instant.now()

  val TableSample = Table(
    name = "name",
    ownerId = SampleUser.id,
    repositoryId = "repositoryId",
    databaseId = "databaseId",
    columns = Seq(),
    status = TableStatus.Active,
    created = DateNow,
    updated = DateNow,
    `type` = TableType.Source,
    inLibrary = true,
    tableStatisticsStatus = TableStatisticsStatus.Pending,
    description = None
  )
  val TableSampleWithId = WithId(TableSample, "id")
  val TableCloneRequestJson = """{"name" : "newName"}"""
  val TableUpdateRequestJson: String =
    """{
      |  "name":"newName",
      |  "columns":[
      |    {
      |      "name":"name"
      |    }
      |  ]
      |}""".stripMargin
  val UpdateColumnRequestSample = UpdateColumnParams("name", None, None, None)

}
