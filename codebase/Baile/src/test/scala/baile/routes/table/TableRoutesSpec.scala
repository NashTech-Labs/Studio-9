package baile.routes.table

import java.time.Instant
import java.util.UUID

import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, Multipart, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import baile.daocommons.WithId
import baile.domain.asset.AssetType
import baile.domain.process.{ Process, ProcessStatus, ResultHandlerMeta }
import baile.domain.table.TableRowValue.StringValue
import baile.domain.table._
import baile.domain.usermanagement.User
import baile.routes.RoutesSpec
import baile.routes.table.util.TestData._
import baile.services.common.{ AuthenticationService, FileUploadService }
import baile.services.remotestorage.{ File, S3StorageService }
import baile.services.table.TableService
import baile.services.table.TableService.TableServiceError._
import baile.services.table.TableService.{ ExportResult, NamedColumnStatistics, TableServiceError, TableStatistics }
import baile.services.tabular.model.TabularModelTrainResultHandler
import baile.services.usermanagement.util.TestData.SampleUser
import cats.implicits._
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito.when
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json.{ JsObject, JsString, Json }

import scala.concurrent.ExecutionContext

class TableRoutesSpec extends RoutesSpec with TableDrivenPropertyChecks {

  private val tableService = mock[TableService]
  private val authenticationService = mock[AuthenticationService]
  private val s3StorageService = mock[S3StorageService]
  private val fileUploadService = new FileUploadService(s3StorageService, "prefix")

  val routes: Route = new TableRoutes(conf, authenticationService, tableService, fileUploadService).routes

  implicit val user = SampleUser

  when(authenticationService.authenticate(userToken)).thenReturn(future(Some(SampleUser)))
  when(s3StorageService.write(any[Source[ByteString, Any]], any[String])(any[ExecutionContext], any[Materializer]))
    .thenReturn(future(File(randomString(), randomInt(20000), Instant.now)))

  "GET /tables/:id" should {

    "return 200 response with table" in {
      when(tableService.get("id", Some("1234"))) thenReturn future(TableSampleWithId.asRight)
      Get("/tables/id?shared_resource_id=1234").signed.check {
        status shouldEqual StatusCodes.OK
        validateTableResponse(responseAs[JsObject])
      }
    }

    "return 403 response" in {
      when(tableService.get("id", Some("1234"))) thenReturn future(TableServiceError.AccessDenied.asLeft)
      Get("/tables/id?shared_resource_id=1234").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "DELETE /tables/:id" should {

    "return 200 response" in {
      when(tableService.delete("id")) thenReturn future(().asRight)
      Delete("/tables/id").signed.check {
        status shouldBe StatusCodes.OK
        responseAs[JsObject] shouldBe JsObject(Seq("id" -> JsString("id")))
      }
    }

    "return 404 response" in {
      when(tableService.delete("id")) thenReturn future(TableServiceError.TableNotFound.asLeft)
      Delete("/tables/id").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /tables" should {

    "return 200 response with table list" in {
      when(tableService.list(None, None, Seq(), 1, 1, None, None)) thenReturn future(
        (Seq(TableSampleWithId), 1).asRight
      )
      Get("/tables?page_size=1").signed.check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[JsObject]
        response.keys should contain allOf("data", "count")
        (response \ "count").as[Int] shouldBe 1
      }
    }

    "return 403 response" in {
      when(tableService.list(None, None, Seq(), 1, 1, None, None)) thenReturn future(
        TableServiceError.AccessDenied.asLeft
      )
      Get("/tables?page_size=1").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /tables/:id/copy" should {

    "return 200 response with table" in {
      when(tableService.cloneTable("newName", "id", Some("1"))) thenReturn future(TableSampleWithId.asRight)
      Post("/tables/id/copy?shared_resource_id=1", Json.parse(TableCloneRequestJson)).signed.check {
        status shouldEqual StatusCodes.OK
        validateTableResponse(responseAs[JsObject])
      }
    }

    "return 400 response when table is not active" in {
      when(tableService.cloneTable(
        "newName",
        "id", Some("1"))) thenReturn future(TableServiceError.TableIsNotActive.asLeft)
      Post("/tables/id/copy?shared_resource_id=1",Json.parse(TableCloneRequestJson)).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "PUT /tables/:id" should {

    "return 200 response from table update " in {
      when(tableService.updateTable(
        "id",
        Some("newName"),
        None,
        Seq(UpdateColumnRequestSample))) thenReturn future(TableSampleWithId.asRight)
      Put("/tables/id", Json.parse(TableUpdateRequestJson)).signed.check {
        status shouldEqual StatusCodes.OK
        validateTableResponse(responseAs[JsObject])
      }
    }

    "return 400 response when table name is empty" in {
      when(tableService.updateTable(
        "id",
        Some("newName"),
        None,
        Seq(UpdateColumnRequestSample))) thenReturn future(TableServiceError.TableNameIsEmpty.asLeft)
      Put("/tables/id", Json.parse(TableUpdateRequestJson)).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return 400 response when table is inactive" in {
      when(tableService.updateTable("id", Some("newName"), None, Seq(UpdateColumnRequestSample))
      ) thenReturn future(TableServiceError.TableIsNotActive.asLeft)
      Put("/tables/id", Json.parse(TableUpdateRequestJson)).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return 400 response when table already exist" in {
      when(tableService.updateTable("id", Some("newName"), None, Seq(UpdateColumnRequestSample))
      ) thenReturn future(TableServiceError.TableNameIsNotUnique("newName").asLeft)
      Put("/tables/id", Json.parse(TableUpdateRequestJson)).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /tables/:id/values" should {

    "return 200 response" in {
      when(tableService.getColumnValues(
        any[String],
        any[String],
        any[Option[String]],
        any[Int],
        any[Option[String]]
      )(any[User])) thenReturn future(Seq(TableRowValue.StringValue("value")).asRight)
      Get("/tables/id/values?column_name=name&search=123&limit=1").signed.check {
        status shouldEqual StatusCodes.OK
        (responseAs[JsObject] \ "data").as[Seq[String]] shouldBe Seq("value")

      }
    }
    "return error response when given column name not found" in {
      when(tableService.getColumnValues(
        any[String],
        any[String],
        any[Option[String]],
        any[Int],
        any[Option[String]]
      )(any[User])) thenReturn future(ColumnNotFound("xyz").asLeft)
      Get("/tables/id/values?column_name=xyz").signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])

      }
    }
  }

  "GET /tables/:id/data" should {

    "return 200 response" in {
      when(tableService.getTableData(
        any[String],
        any[Int],
        any[Int],
        any[Option[String]],
        any[Option[String]],
        any[Option[String]]
      )(any[User])) thenReturn future((Seq(TableRow(Seq(StringValue("some value")))), 1L).asRight)
      Get("/tables/id/data?page_size=1").signed.check {
        status shouldEqual StatusCodes.OK
        (responseAs[JsObject] \ "count").as[Int] shouldBe 1

      }
    }
    "return error response when table is not active" in {
      when(tableService.getTableData(
        any[String],
        any[Int],
        any[Int],
        any[Option[String]],
        any[Option[String]],
        any[Option[String]]
      )(any[User])) thenReturn future(TableIsNotActive.asLeft)
      Get("/tables/id/values?column_name=xyz").signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /tables/:id/stats" should {

    "return 200 response" in {
      val tableStatistics = TableStatistics(
        tableId = randomString(),
        status = randomOf(
          TableStatisticsStatus.Pending,
          TableStatisticsStatus.Error,
          TableStatisticsStatus.Done
        ),
        columnsStatistics = Seq(
          NamedColumnStatistics(randomString(), CategoricalStatistics(
            uniqueValuesCount = randomInt(600),
            categoricalHistogram = CategoricalHistogram(
              Seq(
                CategoricalHistogramRow(Some(randomString()), randomInt(3000)),
                CategoricalHistogramRow(None, randomInt(3000)),
              )
            )
          )),
          NamedColumnStatistics(randomString(), NumericalStatistics(
            min = randomInt(500),
            max = randomInt(300),
            avg = randomInt(300),
            stdPopulation = randomInt(300),
            std = randomInt(300),
            mean = randomInt(300),
            numericalHistogram = NumericalHistogram(
              Seq(
                NumericalHistogramRow(randomInt(20), randomInt(20), randomInt(50))
              )
            )
          ))
        )
      )
      when(tableService.getStatistics(any[String], any[Option[String]])(any[User]))
        .thenReturn(future(tableStatistics.asRight))
      Get("/tables/id/stats").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject].keys should contain allOf("id", "status", "stats")
      }
    }

    "return error response when access to table is denied" in {
      when(tableService.getStatistics(any[String], any[Option[String]])(any[User]))
        .thenReturn(future(AccessDenied.asLeft))
      Get("/tables/id/stats").signed.check {
        status shouldBe StatusCodes.Forbidden
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "GET /tables/:id/stats/process" should {

    "return success response" in {
      val process: WithId[Process] = WithId(Process(
        targetId = "id",
        targetType = AssetType.Table,
        ownerId = user.id,
        authToken = None,
        jobId = UUID.randomUUID(),
        status = ProcessStatus.Running,
        progress = None,
        estimatedTimeRemaining = None,
        created = Instant.now(),
        started = None,
        completed = None,
        errorCauseMessage = None,
        errorDetails = None,
        onComplete = ResultHandlerMeta(classOf[TabularModelTrainResultHandler].getCanonicalName, Json.obj()),
        auxiliaryOnComplete = Seq.empty
      ), "pid")

      when(tableService.getTableStatisticsProcess(any[String])(any[User])).thenReturn(future(Right(process)))

      Get("/tables/id/stats/process").signed.check {
        status shouldEqual StatusCodes.OK
        responseAs[JsObject]
      }
    }

    "return error response when table doesn't exist" in {

      when(tableService.getTableStatisticsProcess(any[String])(any[User])).thenReturn(future(Left(TableNotFound)))

      Get("/tables/id/stats/process").signed.check {
        status shouldEqual StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return error response when table statistics process doesn't exist" in {

      when(tableService.getTableStatisticsProcess(any[String])(any[User]))
        .thenReturn(future(Left(TableStatsProcessNotFound)))

      Get("/tables/id/stats/process").signed.check {
        status shouldEqual StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /tables/import/csv endpoint" should {

    "return success response" in {
      when(tableService.uploadTable(
        any[Map[String, String]],
        any[FileType],
        any[String],
        any[String],
        any[Option[String]],
        any[Option[String]],
        any[Option[Seq[TableService.ColumnInfo]]]
      )(any[User])).thenReturn(future(Right(TableSampleWithId)))

      val name = Multipart.FormData.BodyPart.Strict("name", "xyz")
      val nullValue = Multipart.FormData.BodyPart.Strict("nullValue", "nullValue")
      val fileData = Multipart.FormData.BodyPart.Strict(
        "file", HttpEntity(ContentTypes.`text/plain(UTF-8)`, "This is test file"), Map("fileName" -> "abc.csv"))
      val formData = Multipart.FormData(fileData, name, nullValue)
      Post("/tables/import/csv", formData).signed.check {
        status shouldEqual StatusCodes.OK
        validateTableResponse(responseAs[JsObject])
      }
    }

    "return error response when provided table name is already exist" in {
      when(tableService.uploadTable(
        any[Map[String, String]],
        any[FileType],
        any[String],
        any[String],
        any[Option[String]],
        any[Option[String]],
        any[Option[Seq[TableService.ColumnInfo]]]
      )(any[User])).thenReturn(future(TableServiceError.TableNameIsNotUnique("name").asLeft))
      val name = Multipart.FormData.BodyPart.Strict("name", "xyz")
      val fileData = Multipart.FormData.BodyPart.Strict(
        "file", HttpEntity(ContentTypes.`text/plain(UTF-8)`, "This is test file"), Map("fileName" -> "abc.csv"))
      val formData = Multipart.FormData(fileData, name)
      Post("/tables/import/csv", formData).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /tables/import/json endpoint" should {

    "return success response" in {
      when(tableService.uploadTable(
        any[Map[String, String]],
        any[FileType],
        any[String],
        any[String],
        any[Option[String]],
        any[Option[String]],
        any[Option[Seq[TableService.ColumnInfo]]]
      )(any[User])).thenReturn(future(Right(TableSampleWithId)))

      val json = """[{"name":"name", "displayName":"displayName", "variableType" :"CATEGORICAL", "dataType":"STRING","align":"LEFT"}]"""
      val name = Multipart.FormData.BodyPart.Strict("name", "xyz")
      val nullValue = Multipart.FormData.BodyPart.Strict("nullValue", "nullValue")
      val columnInfo = Multipart.FormData.BodyPart.Strict("columns", json)
      val fileData = Multipart.FormData.BodyPart.Strict(
        "file", HttpEntity(ContentTypes.`text/plain(UTF-8)`, "This is test file"), Map("fileName" -> "abc.csv"))
      val formData = Multipart.FormData(fileData, name, nullValue, columnInfo)
      Post("/tables/import/csv", formData).signed.check {
        status shouldEqual StatusCodes.OK
        validateTableResponse(responseAs[JsObject])
      }
    }

    "return error response when provided table name is already exist" in {
      when(tableService.uploadTable(
        any[Map[String, String]],
        any[FileType],
        any[String],
        any[String],
        any[Option[String]],
        any[Option[String]],
        any[Option[Seq[TableService.ColumnInfo]]]
      )(any[User])).thenReturn(future(TableServiceError.TableNameIsNotUnique("name").asLeft))
      val name = Multipart.FormData.BodyPart.Strict("name", "xyz")
      val fileData = Multipart.FormData.BodyPart.Strict(
        "file", HttpEntity(ContentTypes.`text/plain(UTF-8)`, "This is test file"), Map("fileName" -> "abc.json"))
      val formData = Multipart.FormData(fileData, name)
      Post("/tables/import/json", formData).signed.check {
        status shouldBe StatusCodes.BadRequest
        validateErrorResponse(responseAs[JsObject])
      }
    }

    "return error when provided column name is incorrect" in {
      val columnNames = Table(
        "column",
        "has space",
        "hasCapitals",
        "has_punctuation,",
        "has_русский_буква"
      )

      forAll(columnNames) { columnName =>
        val json = s"""[{"name":"${columnName}", "displayName":"displayName", "variableType" :"CATEGORICAL", "dataType":"STRING","align":"LEFT"}]"""
        val name = Multipart.FormData.BodyPart.Strict("name", "xyz")
        val nullValue = Multipart.FormData.BodyPart.Strict("nullValue", "nullValue")
        val columnInfo = Multipart.FormData.BodyPart.Strict("columns", json)
        val fileData = Multipart.FormData.BodyPart.Strict(
          "file",
          HttpEntity(ContentTypes.`text/plain(UTF-8)`, "This is test file"),
          Map("fileName" -> "abc.csv")
        )
        val formData = Multipart.FormData(fileData, name, nullValue, columnInfo)
        Post("/tables/import/csv", formData).signed.check {
          status shouldBe StatusCodes.BadRequest
          validateErrorResponse(responseAs[JsObject])
        }
      }
    }

  }

  "GET /tables/:id/export" should {
    "return CSV data" in {
      when(tableService.export(eqTo("tableId"), any[Option[String]])(any[User]))
        .thenReturn(future(Right(ExportResult(TableSampleWithId, Source.single(TableRow(Seq(
          TableRowValue.StringValue("foo"),
          TableRowValue.DoubleValue(42D),
          TableRowValue.NullValue
        )))))))

      Get("/tables/tableId/export?access_token=token").signed.check(ContentTypes.`text/csv(UTF-8)`) {
        status shouldBe StatusCodes.OK
      }
    }
    "reject on unknown table" in {
      when(tableService.export(eqTo("tableId"), any[Option[String]])(any[User]))
        .thenReturn(future(Left(TableServiceError.TableNotFound)))

      Get("/tables/tableId/export?access_token=token").signed.check {
        status shouldBe StatusCodes.NotFound
        validateErrorResponse(responseAs[JsObject])
      }
    }
  }

  "POST /tables/:id/save" should {
    "return success response to save table" in {
      val request =
        """
          |{
          |"id": "id",
          |"name": "name"
          |}
        """.stripMargin

      when(tableService.save(
        "id",
        "name"
      )) thenReturn
        future(TableSampleWithId.asRight)

      Post("/tables/id/save", Json.parse(request)).signed.check {
        status shouldBe StatusCodes.OK

      }
    }
    "return error response when table name already exists" in {
      val request =
        """
          |{
          |"name": ""
          |}
        """.stripMargin

      when(tableService.save(
        "id",
        ""
      )) thenReturn
        future(TableServiceError.TableNameIsEmpty.asLeft)

      Post("/tables/id/save", Json.parse(request)).signed.check {
        status shouldBe StatusCodes.BadRequest
      }
    }
  }



  private def validateTableResponse(response: JsObject): Unit = {

    response.fields should contain allOf(
      "id" -> JsString("id"),
      "ownerId" -> JsString(TableSample.ownerId.toString),
      "datasetId" -> JsString(TableSample.databaseId),
      "name" -> JsString(TableSample.name),
      "status" -> JsString(TableSample.status.toString.toUpperCase),
      "datasetType" -> JsString(TableSample.`type`.toString.toUpperCase)
    )
    Instant.parse((response \ "created").as[String]) shouldBe TableSample.created
  }

}
