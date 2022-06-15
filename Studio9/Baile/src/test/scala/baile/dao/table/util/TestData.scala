package baile.dao.table.util

import java.time.Instant

import baile.domain.table._
import baile.services.usermanagement.util.TestData.SampleUser
import org.mongodb.scala.Document
import org.mongodb.scala.bson._

object TestData {

  val ValueRanges = NumericalHistogramRow(
    min = 10.0,
    max = 10.0,
    count = 10
  )

  val NumericalHistogramEntity = NumericalHistogram(Seq(ValueRanges))

  val ValueFrequency = Seq(
    CategoricalHistogramRow(Some("tableValue1"), 10)
  )

  val CategoricalHistogramEntity = CategoricalHistogram(
    rows = ValueFrequency
  )

  val NumericalStatisticsEntity: NumericalStatistics = NumericalStatistics(
    min = 10.0,
    max = 10.0,
    avg = 10.0,
    std = 10.0,
    stdPopulation = 10.0,
    mean = 30.0,
    numericalHistogram = NumericalHistogramEntity
  )

  val CategoricalStatisticsEntity: CategoricalStatistics = CategoricalStatistics(
    uniqueValuesCount = 550l,
    categoricalHistogram = CategoricalHistogramEntity
  )

  val ColumnEntityWithCategoricalStatistics = Column(
    name = "name",
    displayName = "displayName",
    dataType = ColumnDataType.String,
    variableType = ColumnVariableType.Categorical,
    align = ColumnAlign.Right,
    statistics = Some(CategoricalStatisticsEntity)
  )

  val ColumnEntityWithNumericalStatistics = Column(
    name = "name",
    displayName = "displayName",
    dataType = ColumnDataType.String,
    variableType = ColumnVariableType.Continuous,
    align = ColumnAlign.Left,
    statistics = Some(NumericalStatisticsEntity)
  )

  val ColumnWithVariableTypeCategoricalAndAlignRight: Column =
    ColumnEntityWithCategoricalStatistics.copy(
      align = ColumnAlign.Right,
      dataType = ColumnDataType.Boolean
    )

  val ColumnWithAlignCenter: Column = ColumnEntityWithCategoricalStatistics.copy(
    align = ColumnAlign.Center,
    dataType = ColumnDataType.Integer
  )

  val TableEntity = Table(
    ownerId = SampleUser.id,
    name = "name",
    repositoryId = "repositoryId",
    databaseId = "databaseId",
    created = Instant.now(),
    updated = Instant.now(),
    status = TableStatus.Active,
    columns = Seq(ColumnEntityWithCategoricalStatistics),
    `type` = TableType.Source,
    size = Some(0l),
    inLibrary = true,
    tableStatisticsStatus = TableStatisticsStatus.Pending,
    description = None
  )

  val TableEntitySavingAndDerived: Table = TableEntity.copy(
    status = TableStatus.Saving,
    `type` = TableType.Derived,
    columns = Seq(ColumnEntityWithNumericalStatistics)
  )

  val TableEntityWithStatusActive: Table = TableEntity.copy(
    status = TableStatus.Active,
    columns = Seq(ColumnWithVariableTypeCategoricalAndAlignRight),
    tableStatisticsStatus = TableStatisticsStatus.Done
  )

  val TableEntityWithStatusInactive: Table = TableEntity.copy(
    status = TableStatus.Inactive,
    columns = Seq(ColumnWithAlignCenter),
    tableStatisticsStatus = TableStatisticsStatus.Error
  )

  val TableEntityWithStatusError: Table = TableEntity.copy(
    status = TableStatus.Error,
    tableStatisticsStatus = TableStatisticsStatus.Error
  )

  val TableDocument = Document(
    "ownerId" -> TableEntity.ownerId.toString,
    "name" -> TableEntity.name,
    "repositoryId" -> TableEntity.repositoryId,
    "databaseId" -> TableEntity.databaseId,
    "createdDate" -> TableEntity.created.toString,
    "modifiedDate" -> TableEntity.updated.toString,
    "status" -> "ACTIVE",
    "columns" -> Seq(
      Document(
        "name" -> BsonString(ColumnEntityWithCategoricalStatistics.name),
        "displayName" -> BsonString(ColumnEntityWithCategoricalStatistics.displayName),
        "dataType" -> BsonString("STRING"),
        "variableType" -> BsonString("CATEGORICAL"),
        "align" -> BsonString("LEFT"),
        "statistics" -> Document(
          "uniqueValuesCount" -> BsonInt64(550l),
          "categoricalHistogram" -> Seq(
            Document(
              "value" -> BsonString("tableValue1"),
              "count" -> BsonInt64(10)
            )
          )
        )
      )
    ),
    "datasetType" -> BsonString("SOURCE"),
    "size" -> Some(0l),
    "inLibrary" -> true,
    "tableStatisticsStatus" -> "PENDING",
    "description" -> BsonNull()
  )

  val TableDocumentWithStatusError = Document(
    "ownerId" -> TableEntity.ownerId.toString,
    "name" -> TableEntity.name,
    "repositoryId" -> TableEntity.repositoryId,
    "databaseId" -> TableEntity.databaseId,
    "createdDate" -> TableEntity.created.toString,
    "modifiedDate" -> TableEntity.updated.toString,
    "status" -> "ERROR",
    "columns" -> Seq(
      Document(
        "name" -> BsonString(ColumnEntityWithCategoricalStatistics.name),
        "displayName" -> BsonString(ColumnEntityWithCategoricalStatistics.displayName),
        "dataType" -> BsonString("STRING"),
        "variableType" -> BsonString("CATEGORICAL"),
        "align" -> BsonString("RIGHT"),
        "statistics" -> Document(
          "uniqueValuesCount" -> BsonInt64(550l),
          "categoricalHistogram" -> Seq(
            Document(
              "value" -> BsonString("tableValue1"),
              "count" -> BsonInt64(10)
            )
          )
        )
      )
    ),
    "datasetType" -> BsonString("SOURCE"),
    "size" -> Some(0l),
    "inLibrary" -> true,
    "tableStatisticsStatus" -> "ERROR",
    "description" -> BsonNull()
  )

  val TableDocumentWithStatusInactive = Document(
    "ownerId" -> TableEntity.ownerId.toString,
    "name" -> TableEntity.name,
    "repositoryId" -> TableEntity.repositoryId,
    "databaseId" -> TableEntity.databaseId,
    "createdDate" -> TableEntity.created.toString,
    "modifiedDate" -> TableEntity.updated.toString,
    "status" -> "INACTIVE",
    "columns" -> Seq(
      Document(
        "name" -> BsonString(ColumnWithAlignCenter.name),
        "displayName" -> BsonString(ColumnWithAlignCenter.displayName),
        "dataType" -> BsonString("INTEGER"),
        "variableType" -> BsonString("CATEGORICAL"),
        "align" -> BsonString("CENTER"),
        "statistics" -> Document(
          "uniqueValuesCount" -> BsonInt64(550l),
          "categoricalHistogram" -> Seq(
            Document(
              "value" -> BsonString("tableValue1"),
              "count" -> BsonInt64(10)
            )
          )
        )
      )
    ),
    "datasetType" -> BsonString("SOURCE"),
    "size" -> Some(0l),
    "inLibrary" -> true,
    "tableStatisticsStatus" -> "ERROR",
    "description" -> BsonNull()
  )

  val TableDocumentWithSavingAndDerived = Document(
    "ownerId" -> TableEntity.ownerId.toString,
    "name" -> TableEntity.name,
    "repositoryId" -> TableEntity.repositoryId,
    "databaseId" -> TableEntity.databaseId,
    "createdDate" -> TableEntity.created.toString,
    "modifiedDate" -> TableEntity.updated.toString,
    "status" -> "SAVING",
    "columns" -> Seq(
      Document(
        "name" -> BsonString(ColumnEntityWithNumericalStatistics.name),
        "displayName" -> BsonString(ColumnEntityWithNumericalStatistics.displayName),
        "dataType" -> BsonString("STRING"),
        "variableType" -> BsonString("CONTINUOUS"),
        "align" -> BsonString("LEFT"),
        "statistics" -> Document(
          "min" -> BsonNumber(10.0),
          "max" -> BsonNumber(10.0),
          "avg" -> BsonNumber(10.0),
          "std" -> BsonNumber(10.0),
          "stdPopulation" -> BsonNumber(10.0),
          "mean" -> BsonNumber(30.0),
          "numericalHistogram" -> Seq(
            Document(
              "min" -> BsonNumber(10.0),
              "max" -> BsonNumber(10.0),
              "count" -> BsonInt64(10)
            )
          )
        )
      )
    ),
    "datasetType" -> BsonString("DERIVED"),
    "size" -> Some(0l),
    "inLibrary" -> true,
    "tableStatisticsStatus" -> "PENDING",
    "description" -> BsonNull()
  )

  val TableDocumentWithActive = Document(
    "ownerId" -> TableEntity.ownerId.toString,
    "name" -> TableEntity.name,
    "repositoryId" -> TableEntity.repositoryId,
    "databaseId" -> TableEntity.databaseId,
    "createdDate" -> TableEntity.created.toString,
    "modifiedDate" -> TableEntity.updated.toString,
    "status" -> "ACTIVE",
    "columns" -> Seq(
      Document(
        "name" -> BsonString(ColumnWithVariableTypeCategoricalAndAlignRight.name),
        "displayName" -> BsonString(ColumnWithVariableTypeCategoricalAndAlignRight.displayName),
        "dataType" -> BsonString("BOOLEAN"),
        "variableType" -> BsonString("CATEGORICAL"),
        "align" -> BsonString("RIGHT"),
        "statistics" -> Document(
          "uniqueValuesCount" -> BsonInt64(550l),
          "categoricalHistogram" -> Seq(
            Document(
              "value" -> BsonString("tableValue1"),
              "count" -> BsonInt64(10)
            )
          )
        )
      )
    ),
    "datasetType" -> BsonString("SOURCE"),
    "size" -> Some(0l),
    "inLibrary" -> true,
    "tableStatisticsStatus" -> "DONE",
    "description" -> BsonNull()
  )

}
