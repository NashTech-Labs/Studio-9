package sqlserver.dao.table

import java.sql.Timestamp
import java.time.Instant

import akka.stream.scaladsl.Sink
import akka.testkit.TestProbe
import akka.util.Timeout
import cats.implicits._
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.select.Select
import play.api.libs.json._
import sqlserver.BaseSpec
import sqlserver.dao.table.TableDataDao.TableDataDaoError.{ EngineError, ParameterNotFound }
import sqlserver.domain.table.DBValue.{ DBIntValue, DBStringValue }
import sqlserver.domain.table.TableRowValue._
import sqlserver.domain.table.{ Column, ColumnDataType }
import sqlserver.services.dremio.DremioService.{ GetJob, GetJobResultPage, SubmitSqlJob }
import sqlserver.services.dremio.datacontract._

import scala.concurrent.duration._

class DremioTableDataDaoSpec extends BaseSpec {

  trait Setup {

    val dremioActor = TestProbe()
    val dremioRetryDelay = 10.milliseconds
    val dremioRetries = 20
    val dremioTimeout = 3.seconds

    lazy val dao = new DremioTableDataDao(
      dremioActor.ref,
      dremioRetryDelay,
      dremioRetries,
      Timeout(dremioTimeout)
    )
  }

  "DremioTableDataDao#getTableRowSource" should {

    "execute given sql and return source of rows" in new Setup {

      val query =
        CCJSqlParserUtil.parse("SELECT * FROM table WHERE col1 = :param1 AND col2 <> :param2;").asInstanceOf[Select]

      val bindings = Map(
        "param1" -> DBStringValue("foo"),
        "param2" -> DBIntValue(42)
      )

      val pagesCount = 10
      val pageSize = 500
      val recordsCount = pageSize * pagesCount
      val jobId = "id"

      val schema = List(
        DremioColumnResponse("col1", ColumnTypeResponse(ColumnTypeNameResponse.Varchar)),
        DremioColumnResponse("col2", ColumnTypeResponse(ColumnTypeNameResponse.Boolean)),
        DremioColumnResponse("col3", ColumnTypeResponse(ColumnTypeNameResponse.Double)),
        DremioColumnResponse("col4", ColumnTypeResponse(ColumnTypeNameResponse.BigInt)),
        DremioColumnResponse("col5", ColumnTypeResponse(ColumnTypeNameResponse.Timestamp)),
        DremioColumnResponse("col6", ColumnTypeResponse(ColumnTypeNameResponse.Timestamp)),
        DremioColumnResponse("col7", ColumnTypeResponse(ColumnTypeNameResponse.Integer)),
        DremioColumnResponse("col8", ColumnTypeResponse(ColumnTypeNameResponse.Date))
      )

      whenReady {
        val result = dao.getTableRowSource(query, bindings)
        dremioActor.expectMsgType[SubmitSqlJob]
        dremioActor.reply(SQLJobSubmittedResponse(jobId))

        dremioActor.expectMsg(GetJob(jobId))
        dremioActor.reply(buildJobResponse(SQLJobStatusResponse.Starting))
        dremioActor.expectMsg(GetJob(jobId))
        dremioActor.reply(buildJobResponse(SQLJobStatusResponse.Running))
        dremioActor.expectMsg(GetJob(jobId))
        dremioActor.reply(buildJobResponse(SQLJobStatusResponse.Completed))

        dremioActor.expectMsgPF() {
          case GetJobResultPage(`jobId`, _, _) => ()
        }
        dremioActor.reply(
          SQLJobResultResponse(
            rowCount = recordsCount,
            schema = schema,
            rows = List.fill(pageSize)(
              Map(
                "col1" -> JsString("str"),
                "col2" -> JsBoolean(false),
                "col3" -> JsNumber(1.2),
                "col4" -> JsNumber(600l),
                "col5" -> JsString(Timestamp.from(Instant.now()).toString),
                "col6" -> JsNull,
                "col7" -> JsNumber(2),
                "col8" -> JsString("2.10.1999")
              )
            )
          )
        )

        result
      } { eitherResult =>
        val result = eitherResult.right.value

        result.columnsInfo shouldBe Seq(
          Column("col1", ColumnDataType.String),
          Column("col2", ColumnDataType.Boolean),
          Column("col3", ColumnDataType.Double),
          Column("col4", ColumnDataType.Long),
          Column("col5", ColumnDataType.Timestamp),
          Column("col6", ColumnDataType.Timestamp),
          Column("col7", ColumnDataType.Integer),
          Column("col8", ColumnDataType.String)
        )
        result.rowCount shouldBe Some(recordsCount)

        val rowsF = result.source.runWith(Sink.seq)
        (2 to pagesCount).foreach { n =>
          dremioActor.expectMsgPF() {
            case GetJobResultPage(`jobId`, _, _) => ()
          }
          dremioActor.reply(
            SQLJobResultResponse(
              rowCount = recordsCount,
              schema = schema,
              rows = List.fill(pageSize)(
                Map(
                  "col1" -> JsString(s"string #$n 1"),
                  "col2" -> JsBoolean(n % 2 == 0),
                  "col3" -> JsNumber(1.2 * n),
                  "col4" -> JsNumber(600l + n),
                  "col5" -> JsString(Timestamp.from(Instant.now()).toString),
                  "col6" -> JsNull,
                  "col7" -> JsNumber(2 * n),
                  "col8" -> JsString("2.10.1999")
                )
              )
            )
          )
        }

        val results = rowsF.futureValue
        results.length shouldBe recordsCount
        results.foreach { row =>
          row.values.length shouldBe 8
          row.values(0) shouldBe a[StringValue]
          row.values(1) shouldBe a[BooleanValue]
          row.values(2) shouldBe a[DoubleValue]
          row.values(3) shouldBe a[LongValue]
          row.values(4) shouldBe a[TimestampValue]
          row.values(5) shouldBe NullValue
          row.values(6) shouldBe a[IntegerValue]
          row.values(7) shouldBe a[StringValue]
        }
      }
    }

    "return error when parameter not found" in new Setup {
      val query = CCJSqlParserUtil.parse("SELECT * FROM table WHERE col1 = :param1;").asInstanceOf[Select]
      whenReady(dao.getTableRowSource(query, Map())) { result =>
        result shouldBe ParameterNotFound("param1").asLeft
      }
    }

    "return error when sql execution failed" in new Setup {
      val query = CCJSqlParserUtil.parse("SELECT badfunc(*) FROM table;").asInstanceOf[Select]

      val jobId = "id"
      val errorMessage = "Function 'badfunc' does not exist"

      whenReady {
        val result = dao.getTableRowSource(query, Map.empty)
        dremioActor.expectMsgType[SubmitSqlJob]
        dremioActor.reply(SQLJobSubmittedResponse(jobId))

        dremioActor.expectMsg(GetJob(jobId))
        dremioActor.reply(buildJobResponse(SQLJobStatusResponse.Starting))
        dremioActor.expectMsg(GetJob(jobId))
        dremioActor.reply(buildJobResponse(SQLJobStatusResponse.Running))
        dremioActor.expectMsg(GetJob(jobId))
        dremioActor.reply(
          SQLJobResponse(
            SQLJobStatusResponse.Failed,
            errorMessage = Some(errorMessage)
          )
        )

        result
      } { eitherResult =>
        val result = eitherResult.left.value
        result shouldBe an [EngineError]
        result.asInstanceOf[EngineError].throwable.getMessage shouldBe errorMessage
      }
    }

    "throw exception when no retries left" in new Setup {

      override val dremioRetries = 2

      val query = CCJSqlParserUtil.parse("SELECT * FROM table;").asInstanceOf[Select]
      val jobId = "jobId"

      whenReady {
        val result = dao.getTableRowSource(query, Map())

        dremioActor.expectMsgType[SubmitSqlJob]
        dremioActor.reply(SQLJobSubmittedResponse(jobId))

        dremioActor.expectMsg(GetJob(jobId))
        dremioActor.reply(buildJobResponse(SQLJobStatusResponse.Starting))
        dremioActor.expectMsg(GetJob(jobId))
        dremioActor.reply(buildJobResponse(SQLJobStatusResponse.Running))
        dremioActor.expectMsg(GetJob(jobId))
        dremioActor.reply(buildJobResponse(SQLJobStatusResponse.Running))

        result.failed
      } { _.getMessage shouldBe s"Job was not ready after $dremioRetries retries" }
    }

  }

  private def buildJobResponse(jobState: SQLJobStatusResponse): SQLJobResponse =
    SQLJobResponse(jobState, None)

}
