package cortex.api.job.table


// scalastyle:off
object Sample {

  val valueRanges = NumericalHistogramRow(
    min = 10.0,
    max = 100.0,
    count = 100
  )

  val numericalHistogram = NumericalHistogram(Seq(valueRanges))

  val valueFrequency = Seq(
    CategoricalHistogramRow("tableValue1", 10),
    CategoricalHistogramRow("tableValue2", 20),
    CategoricalHistogramRow("tableValue3", 30)
  )

  val categoricalHistogram = CategoricalHistogram(
    valueFrequencies = valueFrequency
  )

  val numericalStatistics = NumericalStatistics(
    min = 20.0,
    max = 100.0,
    avg = 60.0,
    std = 30.0,
    stdPopulation = 40.0,
    mean = 30.0,
    histogram = Some(numericalHistogram)
  )

  val categoricalStatistics = CategoricalStatistics(
    uniqueValuesCount = 550,
    histogram = Some(categoricalHistogram)
  )

  val columns = Seq(
    Column(
      name = "Column1",
      datatype = DataType.STRING
    ),
    Column(
      name = "Column2",
      datatype = DataType.INTEGER
    )
  )

  val variableTypeInfo = VariableTypeInfo(
    variableType = VariableType.CATEGORICAL
  )

  val tableColumns = Seq(
    ColumnInfo(
      name = "Column1",
      displayName = Some("displayName1"),
      datatype = DataType.STRING,
      variableType = Some(variableTypeInfo)
    ),
    ColumnInfo(
      name = "Column2",
      displayName = Some("displayName2"),
      datatype = DataType.INTEGER
    )
  )

  val columnStatistics = Seq(
    ColumnStatistics(
      columnName = "Column1",
      statistics = cortex.api.job.table.ColumnStatistics.Statistics.CategoricalStatistics(categoricalStatistics)
    ),
    ColumnStatistics(
      columnName = "Column2",
      statistics = cortex.api.job.table.ColumnStatistics.Statistics.NumericalStatistics(numericalStatistics)
    )
  )

  val rowCount = 1000

  val tableUploadResponse = TableUploadResponse(
    columns = columns
  )

  val table = Table(
    meta = Some(TableMeta(schema = "main", name = "abalone_train")),
    columns = Seq.empty
  )

  val dataSource = DataSource(
    table = Some(table)
  )

  val tableUploadRequest = TableUploadRequest(
    dataSource = Some(dataSource),
    sourceFilePath = "test/location/csvfile.csv",
    delimeter = ",",
    nullValue = "null",
    fileType = FileType.CSV,
    columns = tableColumns
  )

  val tabularColumnStatisticsRequest = TabularColumnStatisticsRequest(
    dataSource = Some(dataSource),
    columns = columns,
    histogramLength = 120
  )

  val tabularColumnStatisticsResponse = TabularColumnStatisticsResponse(
    columnStatistics = columnStatistics,
    rowCount = rowCount
  )

  val tableMeta = TableMeta(schema = "main", name = "abalone_train")

  val probabilityClassColumn = ProbabilityClassColumn(
    className = "male",
    columnName = "class_male"
  )

}
