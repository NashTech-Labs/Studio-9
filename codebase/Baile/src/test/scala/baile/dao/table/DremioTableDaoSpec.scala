package baile.dao.table

import java.sql.Timestamp
import java.time.Instant

import akka.stream.scaladsl.Sink
import akka.testkit.TestProbe
import akka.util.Timeout
import baile.ExtendedBaseSpec
import baile.domain.table.TableRowValue.{ BooleanValue, DoubleValue, LongValue, NullValue, StringValue, TimestampValue }
import baile.domain.table.{ Column, ColumnAlign, ColumnDataType, ColumnVariableType }
import baile.services.dremio.DremioService.{ GetJobResultPage, GetJob, SubmitSqlJob }
import baile.services.dremio.datacontract.{
  SQLJobResponse, SQLJobResultResponse, SQLJobStatusResponse, SQLJobSubmittedResponse
}
import play.api.libs.json.{ JsBoolean, JsNull, JsNumber, JsString }

import scala.concurrent.duration._
import scala.util.Success

class DremioTableDaoSpec extends ExtendedBaseSpec {

  trait Setup {

    val dremioActor = TestProbe()
    val dremioRetryDelay = 10.milliseconds
    val dremioRetries = 20
    val dremioTimeout = 3.seconds

    val dao = new DremioTableDataDao(
      dremioActor.ref,
      dremioRetryDelay,
      dremioRetries,
      Timeout(dremioTimeout)
    )
  }

  "DremioTableDao#execute" should {

    "execute given sql and return sequence of results" in new Setup {
      val query = "SELECT * FROM table;"

      whenReady {
        val result = dao.execute(query)(_ => Success("result"))

        val jobId = "id"

        dremioActor.expectMsg(SubmitSqlJob(query))
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
        dremioActor.reply(SQLJobResultResponse(
          rowCount = 2,
          rows = List(
            Map.empty,
            Map.empty
          )
        ))

        result
      }(_ shouldBe List("result", "result"))
    }

  }

  "DremioTableDataDao#parseTableRow" should {

    val columns = List(
      Column("col1", "column1", ColumnDataType.String, ColumnVariableType.Categorical, ColumnAlign.Center, None),
      Column("col2", "column2", ColumnDataType.Boolean, ColumnVariableType.Categorical, ColumnAlign.Center, None),
      Column("col3", "column3", ColumnDataType.Double, ColumnVariableType.Categorical, ColumnAlign.Center, None),
      Column("col4", "column4", ColumnDataType.Long, ColumnVariableType.Categorical, ColumnAlign.Center, None),
      Column("col5", "column5", ColumnDataType.Timestamp, ColumnVariableType.Categorical, ColumnAlign.Center, None),
      Column("col6", "column6", ColumnDataType.Timestamp, ColumnVariableType.Categorical, ColumnAlign.Center, None)
    )

    "return row" in new Setup {

      val results = Map(
        "col1" -> JsString("str"),
        "col2" -> JsBoolean(false),
        "col3" -> JsNumber(1.2),
        "col4" -> JsNumber(600l),
        "col5" -> JsString(Timestamp.from(Instant.now()).toString),
        "col6" -> JsNull
      )

      val result = dao.parseTableRow(columns)(results).success.get
      result.values.length shouldBe 6
      result.values(0) shouldBe a[StringValue]
      result.values(1) shouldBe a[BooleanValue]
      result.values(2) shouldBe a[DoubleValue]
      result.values(3) shouldBe a[LongValue]
      result.values(4) shouldBe a[TimestampValue]
      result.values(5) shouldBe NullValue
    }

  }

  "DremioTableDataDao#getTableSource" should {

    "execute given sql and return source of rows" in new Setup {

      val query = "SELECT * FROM table;"

      val columns = List(
        Column("col1", "column1", ColumnDataType.String, ColumnVariableType.Categorical, ColumnAlign.Center, None),
        Column("col2", "column2", ColumnDataType.Boolean, ColumnVariableType.Categorical, ColumnAlign.Center, None),
        Column("col3", "column3", ColumnDataType.Double, ColumnVariableType.Categorical, ColumnAlign.Center, None),
        Column("col4", "column4", ColumnDataType.Long, ColumnVariableType.Categorical, ColumnAlign.Center, None),
        Column("col5", "column5", ColumnDataType.Timestamp, ColumnVariableType.Categorical, ColumnAlign.Center, None),
        Column("col6", "column6", ColumnDataType.Timestamp, ColumnVariableType.Categorical, ColumnAlign.Center, None)
      )

      val pagesCount = 10
      val pageSize = 500
      val recordsCount = pageSize * pagesCount
      val jobId = "id"

      whenReady {
        val result = dao.getTableRowSource(query, columns)
        dremioActor.expectMsg(SubmitSqlJob(query))
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
        dremioActor.reply(SQLJobResultResponse(
          rowCount = recordsCount,
          rows = List.fill(pageSize)(
            Map(
              "col1" -> JsString("str"),
              "col2" -> JsBoolean(false),
              "col3" -> JsNumber(1.2),
              "col4" -> JsNumber(600l),
              "col5" -> JsString(Timestamp.from(Instant.now()).toString),
              "col6" -> JsNull
            )
          )
        ))

        result
      }{ source =>
        val resultsF = source.runWith(Sink.seq)
        (2 to pagesCount).foreach { n =>
          dremioActor.expectMsgPF() {
            case GetJobResultPage(`jobId`, _, _) => ()
          }
          dremioActor.reply(SQLJobResultResponse(
            rowCount = recordsCount,

            rows = List.fill(pageSize)(
              Map(
                "col1" -> JsString(s"string #$n 1"),
                "col2" -> JsBoolean(n % 2 == 0),
                "col3" -> JsNumber(1.2 * n),
                "col4" -> JsNumber(600l + n),
                "col5" -> JsString(Timestamp.from(Instant.now()).toString),
                "col6" -> JsNull
              )
            )
          ))
        }

        val results = resultsF.futureValue
        results.length shouldBe recordsCount
        results.foreach { row =>
          row.values.length shouldBe 6
          row.values(0) shouldBe a[StringValue]
          row.values(1) shouldBe a[BooleanValue]
          row.values(2) shouldBe a[DoubleValue]
          row.values(3) shouldBe a[LongValue]
          row.values(4) shouldBe a[TimestampValue]
          row.values(5) shouldBe NullValue
        }
      }
    }


  }

  "DremioTableDataDao#parseTableRowCount" should {

    "parse rows count from result map" in new Setup {
      val count = 2048
      val result = Map(
        "$RES" -> JsNumber(count)
      )

      dao.parseTableRowCount(result).success.get shouldBe count
    }

  }
  
  private def buildJobResponse(jobState: SQLJobStatusResponse): SQLJobResponse =
    SQLJobResponse(jobState, None)

}
