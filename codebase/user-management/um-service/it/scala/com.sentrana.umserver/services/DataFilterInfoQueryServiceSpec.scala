package com.sentrana.umserver.services

import com.sentrana.umserver.exceptions.DataRetrievalException
import com.sentrana.umserver.shared.dtos.enums.DBType
import com.sentrana.umserver.{IntegrationTestUtils, OneAppWithMongo, WithSQLSupport}
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers._

/**
  * Created by Alexander on 04.07.2016.
  */
class DataFilterInfoQueryServiceSpec extends PlaySpec with OneAppWithMongo with WithSQLSupport {

  private val tableName = "data_by_column_names"
  private val js =
    """
      |[
      | { $match: { fieldName: "testFieldMongoType" } },
      | { $project: { _id: "$id", "value": "$valuesQuerySettings.dbType", "displayText": "$valuesQuerySettings.collectionName" } }
      | { $sort: { _id: -1 } }
      |]
    """.stripMargin

  private val mongoDb = "um"

  private lazy val mongoDbConfig = Map(
    s"mongo.${mongoDb}.uri" -> mongoConfig("mongodb.um.uri"),
    s"mongo.${mongoDb}.db" -> "test")

  private lazy val appWithSpecificProperties = app.copy(additionalConfiguration = app.additionalConfiguration ++ sqlConfig ++ mongoDbConfig)
  private lazy val dataFilterInfoQueryService = appWithSpecificProperties.injector.instanceOf(classOf[DataFilterInfoQueryService])
  protected lazy val connectionProvider = appWithSpecificProperties.injector.instanceOf(classOf[ScalikeQueryExecutor])

  private lazy val itUtils = new IntegrationTestUtils()
  private lazy val testDataFilterInfoWithWrongMongoCollectionName = itUtils.createTestDataFilterInfo("testField", dbName = mongoDb)
  private lazy val testDataFilterInfoWithWrongSqlCollectionName = itUtils.createTestDataFilterInfo("testField", dbName = "testCollection", dbType = DBType.SQL)

  private lazy val testDataFilterInfoWithSQLType = itUtils.createTestDataFilterInfo("testFieldSqlType",
    validValuesQuery = getValidValuesQuery(tableName),
    dbName = connectionName,
    dbType = DBType.SQL
  )

  private lazy val testDataFilterInfoWithMongoType = itUtils.createTestDataFilterInfo("testFieldMongoType",
    validValuesQuery = js,
    dbName = mongoDb,
    collectionName = Option("dataFilters"))

  private lazy val testDataFilterInfoWithMongoTypeAndEmptyCollection = itUtils.createTestDataFilterInfo("testFieldMongoTypeAndEmptyCollection",
    validValuesQuery = js,
    dbName = mongoDb)

  "DataFilterInfoQueryService" must {
    "wrap exceptions into DataRetrievalException for MONGO dbType" in {
        intercept[DataRetrievalException] {
        await(dataFilterInfoQueryService.getValidValues(testDataFilterInfoWithWrongMongoCollectionName.id))
      }
      ()
    }

    "wrap exceptions into DataRetrievalException for SQL dbType" in {
      intercept[DataRetrievalException] {
        await(dataFilterInfoQueryService.getValidValues(testDataFilterInfoWithWrongSqlCollectionName.id))
      }
      ()
    }

    "add test data" in {
      val column1 = "value"
      val column2 = "display_text"

      connectionProvider.addConnectionPool(connectionName)
      executeQuery(connectionName, createTableQuery(tableName, column1, column2))
      executeQuery(connectionName, insertQuery(tableName, column1, column2, "test_value1", "test_display_text1"))
      executeQuery(connectionName, insertQuery(tableName, column1, column2, "test_value2", "test_display_text2"))
    }

    "get valid values for dataFilter with SQL DBType" in {
      val result = await(dataFilterInfoQueryService.getValidValues(testDataFilterInfoWithSQLType.id))
      result.size mustBe 2
    }

    "get valid values for dataFilter with Mongo DBType" in {
      val result = await(dataFilterInfoQueryService.getValidValues(testDataFilterInfoWithMongoType.id))
      result.size mustBe 1
      result must contain only("MONGO" -> "dataFilters")
    }

    "throw exception if collection isn't specified" in {
      intercept[DataRetrievalException] {
        await(dataFilterInfoQueryService.getValidValues(testDataFilterInfoWithMongoTypeAndEmptyCollection.id))
      }
      ()
    }

    "split javascript string array of objects into a list of javascript object strings" in {
      dataFilterInfoQueryService.parseMongoAllowableConnectionsString(js).size mustBe 3
    }
  }
}
