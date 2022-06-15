package sqlserver.query.routes

import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.{ ContentTypes, HttpHeader, StatusCodes }
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json.{ JsObject, Json }
import sqlserver.domain.table.DBValue._
import sqlserver.domain.table._
import sqlserver.routes.RoutesSpec
import sqlserver.routes.query.QueryRoutes
import sqlserver.services.query.QueryService
import sqlserver.services.query.QueryService.ExecuteError

class QueryRoutesSpec extends RoutesSpec with TableDrivenPropertyChecks {

  trait Setup extends RoutesSetup {
    val service: QueryService = mock[QueryService]
    val routes: Route = new QueryRoutes(service).routes
  }

  "POST /query endpoint" should {

    "return current status" in new Setup {
      val columns = Seq(
        Column("id", ColumnDataType.String),
        Column("name", ColumnDataType.String),
        Column("married", ColumnDataType.Boolean),
        Column("age", ColumnDataType.Integer),
        Column("weight", ColumnDataType.Double),
        Column("visited", ColumnDataType.Timestamp)
      )

      val row1 = Seq(
        TableRowValue.StringValue("1"),
        TableRowValue.StringValue("joe"),
        TableRowValue.BooleanValue(false),
        TableRowValue.LongValue(23),
        TableRowValue.DoubleValue(12.3),
        TableRowValue.TimestampValue("2007-12-03T10:15:30.00Z")
      )

      val row2 = Seq(
        TableRowValue.StringValue("2"),
        TableRowValue.StringValue("mark"),
        TableRowValue.BooleanValue(false),
        TableRowValue.IntegerValue(2),
        TableRowValue.DoubleValue(3.31),
        TableRowValue.NullValue
      )

      val row3 = Seq(
        TableRowValue.StringValue("3"),
        TableRowValue.StringValue("lora"),
        TableRowValue.BooleanValue(true),
        TableRowValue.IntegerValue(28),
        TableRowValue.DoubleValue(10.1),
        TableRowValue.TimestampValue("2017-12-03T10:15:30.00Z")
      )

      val row4 = Seq(
        TableRowValue.StringValue("4"),
        TableRowValue.StringValue("null"),
        TableRowValue.BooleanValue(true),
        TableRowValue.IntegerValue(55),
        TableRowValue.DoubleValue(14.3),
        TableRowValue.TimestampValue("2017-11-03T10:15:30.00Z")
      )

      val bindings = Map(
        "bind1" -> DBStringValue("value"),
        "bind2" -> DBBooleanValue(true),
        "bind3" -> DBIntValue(2),
        "bind4" -> DBDoubleValue(123232432434L)
      )

      service.execute(*, *, *, bindings) shouldReturn future(
        Right(
          TableQueryResult(
            Source(
              List(
                TableRow(row1),
                TableRow(row2),
                TableRow(row3),
                TableRow(row4)
              )
            ),
            columns,
            Some(4)
          )
        )
      )

      private val request =
        """
          |{
          |"query" : "select * from test where id = :bind1",
          |"token" : "token",
          |"tables" : { "test" : "table" },
          |"bindings" :
          |{
          |"bind1" : "value",
          |"bind2" : true,
          |"bind3" : 2,
          |"bind4" : 123232432434
          |}
          |}
        """.stripMargin

      val expectedHeaders: Seq[HttpHeader] = Seq(
        RawHeader(
          "X-SQL-Columns",
          "[" +
            """{"name":"id","type":"STRING"},""" +
            """{"name":"name","type":"STRING"},""" +
            """{"name":"married","type":"BOOLEAN"},""" +
            """{"name":"age","type":"INTEGER"},""" +
            """{"name":"weight","type":"DOUBLE"},""" +
            """{"name":"visited","type":"TIMESTAMP"}""" +
            "]"
        ),
        RawHeader("X-SQL-Rows-Count", "4")
      )

      val expectedBody =
        "1,joe,false,23,12.3,2007-12-03T10:15:30.00Z\r\n" +
          "2,mark,false,2,3.31,null\r\n" +
          "3,lora,true,28,10.1,2017-12-03T10:15:30.00Z\r\n" +
          """4,null,true,55,14.3,2017-11-03T10:15:30.00Z""" + "\r\n"

      Post("/query", Json.parse(request)).check(ContentTypes.`text/csv(UTF-8)`) {
        status shouldBe StatusCodes.OK
        response.headers shouldEqual expectedHeaders
        val body = responseEntity.dataBytes.runWith(Sink.reduce[ByteString](_ ++ _)).futureValue.utf8String
        body shouldBe expectedBody
      }
    }

    "return error if service execute method fails" in new Setup {
      val request =
        """
          |{
          |"query": "select * from table",
          |"tables": {},
          |"token": "invalid-token"
          |}
        """.stripMargin

      val table = Table(
        ("service error", "status code"),
        (ExecuteError.SqlIsNotSelect, StatusCodes.BadRequest),
        (ExecuteError.InvalidSql("invalid sql"), StatusCodes.BadRequest),
        (ExecuteError.TableIdNotProvided("table1"), StatusCodes.BadRequest),
        (ExecuteError.ParameterNotFound("bind1"), StatusCodes.BadRequest),
        (ExecuteError.EngineError(new Exception("error")), StatusCodes.BadRequest),
        (ExecuteError.Unauthenticated, StatusCodes.Unauthorized),
        (ExecuteError.AccessDenied, StatusCodes.Unauthorized),
        (ExecuteError.TableNotFound("table1"), StatusCodes.NotFound)
      )
      forAll(table) { (serviceError, statusCode) =>
        service.execute(*, *, *, *) shouldReturn future(Left(serviceError))

        Post("/query", Json.parse(request)).check {
          status shouldBe statusCode
          validateErrorResponse(responseAs[JsObject])
        }
      }
    }
  }

}
