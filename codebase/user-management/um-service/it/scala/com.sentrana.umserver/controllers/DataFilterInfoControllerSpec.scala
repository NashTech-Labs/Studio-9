package com.sentrana.umserver.controllers

import com.sentrana.umserver.services.{DataFilterInfoQueryService, MongoDbService, ScalikeQueryExecutor}
import com.sentrana.umserver.shared.dtos.{DataFilterInfo, DataFilterInfoSettings}
import com.sentrana.umserver.shared.dtos.enums.DBType
import com.sentrana.umserver.{OneServerWithMongo, WithAdminUser, WithSQLSupport}
import org.scalatestplus.play.PlaySpec
import play.api.libs.ws.{WS, WSResponse}
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by Alexander on 25.05.2016.
  */
class DataFilterInfoControllerSpec extends PlaySpec with OneServerWithMongo with WithAdminUser with WithSQLSupport {

  import com.sentrana.umserver.JsonFormats._
  import com.sentrana.umserver.entities.MongoFormats.dataFilterInfoMongoFormat

  private lazy val DATA_FILTERS_BASE_URL = s"$baseUrl/filters"

  private lazy val appWithSpecificProperties = app.copy(additionalConfiguration = app.additionalConfiguration ++ sqlConfig)
  private lazy val dfiQueryService = appWithSpecificProperties.injector.instanceOf(classOf[DataFilterInfoQueryService])
  private lazy val mongoDbService = appWithSpecificProperties.injector.instanceOf(classOf[MongoDbService])
  protected lazy val connectionProvider = appWithSpecificProperties.injector.instanceOf(classOf[ScalikeQueryExecutor])

  private var dataFilterIds: Seq[String] = _
  private val tableName = "data_by_column_names"

  private val dataFilterInfoSample = DataFilterInfo(
    "",
    "testFieldName",
    "testFieldDesc",
    valuesQuerySettings = Option(DataFilterInfoSettings(
      validValuesQuery = getValidValuesQuery(tableName),
      dbName = connectionName,
      dbType = DBType.SQL,
      "testDataType",
      Some("TestCollectionName"))),
    Some("TestDisplayName"),
    false
  )

  private var dataFilterInfoId: String = _

  "DataFilterInfoController" must {
    "return 404 when trying to get non existing data filter" in {
      rootOrg

      val resp = await(WS.url(DATA_FILTERS_BASE_URL + "/noSuchApp").withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + resp.body) {
        resp.status mustBe (NOT_FOUND)
      }
    }

    "create new data filter" in {
      val resp = await(createDataFilterInfo("testFieldName"))
      withClue("Response body: " + resp.body) {
        resp.status mustBe (OK)
      }
      dataFilterInfoId = (resp.json \ "id").as[String]
      dataFilterInfoId must not be empty
    }

    "read freshly created data filter" in {
      val resp = await(WS.url(DATA_FILTERS_BASE_URL + "/" + dataFilterInfoId).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + resp.body) {
        resp.status mustBe (OK)
      }
      (resp.json \ "fieldName").as[String] mustBe dataFilterInfoSample.fieldName
      (resp.json \ "fieldDesc").as[String] mustBe dataFilterInfoSample.fieldDesc
      (resp.json \ "displayName").asOpt[String] mustBe dataFilterInfoSample.displayName
      (resp.json \ "showValueOnly").as[Boolean] mustBe dataFilterInfoSample.showValueOnly

      (resp.json \ "valuesQuerySettings" \ "validValuesQuery").as[String] mustBe dataFilterInfoSample.valuesQuerySettings.value.validValuesQuery
      (resp.json \ "valuesQuerySettings" \ "dbName").as[String] mustBe dataFilterInfoSample.valuesQuerySettings.value.dbName
      (resp.json \ "valuesQuerySettings" \ "dbType").as[String] mustBe dataFilterInfoSample.valuesQuerySettings.value.dbType.name()
      (resp.json \ "valuesQuerySettings" \ "dataType").as[String] mustBe dataFilterInfoSample.valuesQuerySettings.value.dataType
      (resp.json \ "valuesQuerySettings" \ "collectionName").asOpt[String] mustBe dataFilterInfoSample.valuesQuerySettings.value.collectionName
    }

    "update some data filters fields" in {
      val fakeDesc = "fake fieldDesc"
      val resp = await(WS.url(DATA_FILTERS_BASE_URL + "/" + dataFilterInfoId).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).put(
        s"""
           |{
           |"fieldDesc": "${fakeDesc}"
           |}
        """.stripMargin
      ))
      withClue("Response body: " + resp.body) {
        resp.status mustBe (OK)
      }
      val actualDataFilterInfo = await(dfiQueryService.get(dataFilterInfoId)).value
      println(s"actualDataFilterInfo ${actualDataFilterInfo.valuesQuerySettings}")
      println(s"expected ${dataFilterInfoSample.valuesQuerySettings}")

      verifyDataFilterInfo(actualDataFilterInfo, dataFilterInfoSample.copy(fieldDesc = fakeDesc))
    }

    "update all data filters fields" in {
      val dataFilterInfoSampleUpdate = DataFilterInfo(
        "",
        "fake testFieldName",
        "fake testFieldDesc",
        Option(DataFilterInfoSettings(
          "fake testValidValuesQuery",
          "fake testDbName",
          DBType.SQL,
          "fake testDataType",
          Some("fake TestCollectionName")
        )),
        Some("fake TestDisplayName"),
        true
      )
      val resp = await(WS.url(DATA_FILTERS_BASE_URL + "/" + dataFilterInfoId).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).put(
        s"""
          {
           |"fieldName": "${dataFilterInfoSampleUpdate.fieldName}",
           |"fieldDesc": "${dataFilterInfoSampleUpdate.fieldDesc}",
           |"displayName": "${dataFilterInfoSampleUpdate.displayName.value}",
           |"showValueOnly": ${dataFilterInfoSampleUpdate.showValueOnly},
           |"valuesQuerySettings": {
           |  "validValuesQuery": "${dataFilterInfoSampleUpdate.valuesQuerySettings.value.validValuesQuery}",
           |  "dbName": "${dataFilterInfoSampleUpdate.valuesQuerySettings.value.dbName}",
           |  "dbType": "${dataFilterInfoSampleUpdate.valuesQuerySettings.value.dbType}",
           |  "dataType": "${dataFilterInfoSampleUpdate.valuesQuerySettings.value.dataType}",
           |  "collectionName": "${dataFilterInfoSampleUpdate.valuesQuerySettings.value.collectionName.value}"
           | }
           |}
        """.stripMargin
      ))
      withClue("Response body: " + resp.body) {
        resp.status mustBe (OK)
      }
      val actualDataFilterInfo = await(dfiQueryService.get(dataFilterInfoId)).value
      verifyDataFilterInfo(actualDataFilterInfo, dataFilterInfoSampleUpdate)
    }

    "reset valuesQuerySettings to None" in {
      val dataFilterInfoSampleUpdate = DataFilterInfo(
        "",
        "fake testFieldName",
        "fake testFieldDesc",
        Option(DataFilterInfoSettings(
          "fake testValidValuesQuery",
          "fake testDbName",
          DBType.SQL,
          "fake testDataType",
          Some("fake TestCollectionName")
        )),
        Some("fake TestDisplayName"),
        true
      )
      val resp = await(WS.url(DATA_FILTERS_BASE_URL + "/" + dataFilterInfoId).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).put(
        s"""
          {
           |"fieldName": "${dataFilterInfoSampleUpdate.fieldName}",
           |"fieldDesc": "${dataFilterInfoSampleUpdate.fieldDesc}",
           |"displayName": "${dataFilterInfoSampleUpdate.displayName.value}",
           |"showValueOnly": ${dataFilterInfoSampleUpdate.showValueOnly},
           |"resetValuesQuerySettings": true,
           |"valuesQuerySettings": {
           |  "validValuesQuery": "${dataFilterInfoSampleUpdate.valuesQuerySettings.value.validValuesQuery}",
           |  "dbName": "${dataFilterInfoSampleUpdate.valuesQuerySettings.value.dbName}",
           |  "dbType": "${dataFilterInfoSampleUpdate.valuesQuerySettings.value.dbType}",
           |  "dataType": "${dataFilterInfoSampleUpdate.valuesQuerySettings.value.dataType}",
           |  "collectionName": "${dataFilterInfoSampleUpdate.valuesQuerySettings.value.collectionName.value}"
           | }
           |}
        """.stripMargin
      ))
      withClue("Response body: " + resp.body) {
        resp.status mustBe (OK)
      }
      val actualDataFilterInfo = await(dfiQueryService.get(dataFilterInfoId)).value
      verifyDataFilterInfo(actualDataFilterInfo, dataFilterInfoSampleUpdate.copy(valuesQuerySettings = None))
    }

    "delete dataFilterInfo by id" in {
      await(WS.url(DATA_FILTERS_BASE_URL + "/" + dataFilterInfoId).withQueryString("access_token" -> adminToken).delete())
      await(dfiQueryService.get(dataFilterInfoId)) mustBe empty
    }

    "create additional dataFilters" in {
      val dataFilterInfos = (1 to 6).map { i =>
        val dataFilterInfo = dataFilterInfoSample.copy(id = mongoDbService.generateId,
          valuesQuerySettings = Option(DataFilterInfoSettings(
            validValuesQuery = if(i == 2) "select 1" else dataFilterInfoSample.valuesQuerySettings.value.validValuesQuery,
            dbName = dataFilterInfoSample.valuesQuerySettings.value.dbName,
            dbType = dataFilterInfoSample.valuesQuerySettings.value.dbType,
            dataType = dataFilterInfoSample.valuesQuerySettings.value.dataType,
            collectionName = dataFilterInfoSample.valuesQuerySettings.value.collectionName
          ))
        )
        mongoDbService.save(dataFilterInfo)
        dataFilterInfo
      }
      dataFilterIds = dataFilterInfos.map{_.id}
    }

    "retrieve dataFilters info by multiple ids" in {
      baseDataFilterFindSpec(DATA_FILTERS_BASE_URL + s"?id=${dataFilterIds(0)}&id=${dataFilterIds(1)}", 2)
    }

    "retrieve dataFilters info by single id" in {
      baseDataFilterFindSpec(DATA_FILTERS_BASE_URL + s"?id=${dataFilterIds(0)}", 1)
    }

    "retrieve dataFilters info by ids with limit" in {
      baseDataFilterFindSpec(DATA_FILTERS_BASE_URL + s"?limit=1&id=${dataFilterIds(0)}&id=${dataFilterIds(1)}", 1)
    }

    "retrieve dataFilters info by ids with skip" in {
      baseDataFilterFindSpec(DATA_FILTERS_BASE_URL + s"?offset=1&id=${dataFilterIds(0)}&id=${dataFilterIds(1)}", 1)
    }

    "retrieve dataFilters just with limit" in {
      val res = await(WS.url(DATA_FILTERS_BASE_URL + s"?limit=4").withQueryString("access_token" -> adminToken).get())
      val dataFilterInfosParsed = (res.json \ "data").as[Seq[DataFilterInfo]]
      dataFilterInfosParsed.size mustBe 4
    }

    "init DB data" in {
      executeQuery(connectionName, createTableQuery(tableName, "value", "display_text"))
      executeQuery(connectionName, insertQuery(tableName, "value", "display_text", "test_value1", "test_display_text1"))
      executeQuery(connectionName, insertQuery(tableName, "value", "display_text", "test_value2", "test_display_text2"))
    }

    "get valid values from query" in {
      val resp = await(WS.url(DATA_FILTERS_BASE_URL + s"/${dataFilterIds(0)}/values").withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (OK) }
      (resp.json \ "test_value1").as[String] mustBe "test_display_text1"
      (resp.json \ "test_value2").as[String] mustBe "test_display_text2"
    }

    "return 400 if no valid values are present" in {
      val resp = await(WS.url(DATA_FILTERS_BASE_URL + s"/${dataFilterIds(1)}/values").withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + resp.body) { resp.status mustBe (BAD_REQUEST) }
    }

    "return 404 when getting valid values for non existing dataFilter" in {
      val resp = await(WS.url(DATA_FILTERS_BASE_URL + s"/${dataFilterIds(0) + "_s"}/values").withQueryString("access_token" -> adminToken).get())
      withClue("Response body: " + resp.body) {
        resp.status mustBe (NOT_FOUND)
      }
    }
  }

  private def baseDataFilterFindSpec(queryString: String, expectedSize: Int): Unit = {
    val res = await(WS.url(queryString).withQueryString("access_token" -> adminToken).get())
    val dataFilterInfosParsed = (res.json \ "data").as[Seq[DataFilterInfo]]
    dataFilterInfosParsed.size mustBe expectedSize
  }

  private def createDataFilterInfo(fieldName: String): Future[WSResponse] = {
    WS.url(DATA_FILTERS_BASE_URL).withHeaders(("Content-Type", "application/json")).withQueryString("access_token" -> adminToken).post(
      s"""
         |{
         |"fieldName": "$fieldName",
         |"fieldDesc": "${dataFilterInfoSample.fieldDesc}",
         |"displayName": "${dataFilterInfoSample.displayName.value}",
         |"showValueOnly": ${dataFilterInfoSample.showValueOnly},
         |"valuesQuerySettings": {
           |"validValuesQuery": "${dataFilterInfoSample.valuesQuerySettings.value.validValuesQuery}",
           |"dbName": "${dataFilterInfoSample.valuesQuerySettings.value.dbName}",
           |"dbType": "${dataFilterInfoSample.valuesQuerySettings.value.dbType}",
           |"dataType": "${dataFilterInfoSample.valuesQuerySettings.value.dataType}",
           |"collectionName": "${dataFilterInfoSample.valuesQuerySettings.value.collectionName.value}"
         | }
         |}
        """.stripMargin
    )
  }

  private def createDataFilterInfos(amount: Int): Future[Seq[WSResponse]] = {
    val dataFilterInfoWsResponses = (1 to amount).map { i =>
      createDataFilterInfo("testFieldName" + i)
    }
    Future.sequence(dataFilterInfoWsResponses)
  }

  private def verifyDataFilterInfo(actualDataFilterInfo: DataFilterInfo, expectedDataFilterInfo: DataFilterInfo): Unit = {
    actualDataFilterInfo.id mustBe dataFilterInfoId
    actualDataFilterInfo.fieldDesc mustBe expectedDataFilterInfo.fieldDesc
    actualDataFilterInfo.fieldName mustBe expectedDataFilterInfo.fieldName
    actualDataFilterInfo.showValueOnly mustBe expectedDataFilterInfo.showValueOnly
    actualDataFilterInfo.valuesQuerySettings.isDefined mustBe expectedDataFilterInfo.valuesQuerySettings.isDefined

    for {
      actualDataFilterInfoSettings <- actualDataFilterInfo.valuesQuerySettings
      expectedDataFilterInfoSettings <- expectedDataFilterInfo.valuesQuerySettings
    } {
      actualDataFilterInfoSettings.collectionName mustBe expectedDataFilterInfoSettings.collectionName
      actualDataFilterInfoSettings.dataType mustBe expectedDataFilterInfoSettings.dataType
      actualDataFilterInfoSettings.dbName mustBe expectedDataFilterInfoSettings.dbName
      actualDataFilterInfoSettings.dbType mustBe expectedDataFilterInfoSettings.dbType
      actualDataFilterInfoSettings.validValuesQuery mustBe expectedDataFilterInfoSettings.validValuesQuery
      ()
    }
    actualDataFilterInfo.displayName mustBe expectedDataFilterInfo.displayName
    ()
  }
}
