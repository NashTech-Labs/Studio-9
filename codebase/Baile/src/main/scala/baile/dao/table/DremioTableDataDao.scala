package baile.dao.table

import akka.NotUsed
import akka.actor.{ ActorRef, Scheduler }
import akka.pattern.ask
import akka.pattern.after
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout
import baile.domain.table.TableRowValue._
import baile.domain.table.{ Column, ColumnDataType, TableRow }
import baile.services.dremio.DremioService.{ GetJobResultPage, GetJob, SubmitSqlJob }
import baile.services.dremio.datacontract.{ SQLJobResponse, SQLJobResultResponse, SQLJobSubmittedResponse }
import baile.services.dremio.datacontract.SQLJobStatusResponse._
import baile.utils.TryExtensions._
import play.api.libs.json.{ JsNull, JsValue }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

// TODO Optimize not to work with Map[String, JsValue], but with List[JsValue]
// TODO ONLY if dremio always return columns in requested order
class DremioTableDataDao(
  dremioService: ActorRef,
  dremioRetryDelay: FiniteDuration,
  dremioMaxRetries: Int,
  implicit val dremioServiceTimeout: Timeout
)(
  implicit ec: ExecutionContext,
  scheduler: Scheduler,
  materializer: Materializer
) extends SQLTableDataDao[Map[String, JsValue]] {

  private val maxRowsLimit = 500

  override protected[table] def execute[T](
    query: String
  )(resultParser: Map[String, JsValue] => Try[T])(implicit ec: ExecutionContext): Future[Seq[T]] =
    for {
      jobId <- (dremioService ? SubmitSqlJob(query))
        .mapTo[SQLJobSubmittedResponse]
        .map(_.id)
      firstResult <- pollJobResults(jobId)
      source = createResultsSource(jobId, firstResult)
      rows <- source.runWith(Sink.seq)
      result <- Try.sequence(rows.map(resultParser)).toFuture
    } yield result

  override protected[table] def parseTableRow(
    columns: Seq[Column]
  )(results: Map[String, JsValue]): Try[TableRow] = Try {
    val values = columns.map { column =>
      val resultValue = results(column.name)
      resultValue match {
        case JsNull =>
          NullValue
        case jsValue =>
          column.dataType match {
            case ColumnDataType.String => StringValue(jsValue.as[String])
            case ColumnDataType.Integer => IntegerValue(jsValue.as[Int])
            case ColumnDataType.Boolean => BooleanValue(jsValue.as[Boolean])
            case ColumnDataType.Double => DoubleValue(jsValue.as[Double])
            case ColumnDataType.Long => LongValue(jsValue.as[Long])
            case ColumnDataType.Timestamp => TimestampValue(jsValue.as[String])
          }
      }
    }

    TableRow(values.toSeq)
  }

  override protected[table] def getTableRowSource(
    query: String,
    columns: Seq[Column]
  )(implicit ec: ExecutionContext): Future[Source[TableRow, NotUsed]] =
    for {
      jobId <- (dremioService ? SubmitSqlJob(query))
        .mapTo[SQLJobSubmittedResponse]
        .map(_.id)
      firstResult <- pollJobResults(jobId)
    } yield createResultsSource(jobId, firstResult).map { results =>
      parseTableRow(columns)(results).get
    }

  override protected[table] def parseTableRowCount(results: Map[String, JsValue]): Try[Long] = Try {
    results.head match {
      case (_, count) => count.as[Long]
    }
  }

  private def pollJobResults(jobId: String): Future[SQLJobResultResponse] = {

    def poll(retriesLeft: Int): Future[SQLJobResultResponse] =
      for {
        job <- (dremioService ? GetJob(jobId)).mapTo[SQLJobResponse]
        result <- job.jobState match {
          case Enqueued | Starting | Running =>
            if (retriesLeft > 0) {
              after(dremioRetryDelay, scheduler)(poll(retriesLeft - 1))
            } else {
              throw new RuntimeException(s"Job was not ready after $dremioMaxRetries retries")
            }
          case Completed =>
            (dremioService ? GetJobResultPage(
              jobId = jobId,
              offset = 0,
              limit = maxRowsLimit
            )).mapTo[SQLJobResultResponse]
          case unexpected =>
            val aboutError = job.errorMessage.fold("") { message =>
              " Error details: " + message
            }
            throw new RuntimeException(s"Unexpected status $unexpected.$aboutError")
        }
      } yield result

    poll(dremioMaxRetries)
  }

  private def createResultsSource(
    jobId: String,
    firstResult: SQLJobResultResponse
  ): Source[Map[String, JsValue], NotUsed] = {

    val totalRows = firstResult.rowCount

    def readMoreResults(offset: Int): Future[SQLJobResultResponse] =
      (dremioService ? GetJobResultPage(
        jobId = jobId,
        offset = offset,
        limit = maxRowsLimit
      )).mapTo[SQLJobResultResponse]

    Source.unfoldAsync((firstResult.rows.length, 0)) { case (rowsRead, offset) =>
      if (rowsRead == totalRows) {
        Future.successful(None)
      } else {
        val nextOffset = offset + maxRowsLimit
        readMoreResults(offset + maxRowsLimit).map { nextResult =>
          Some((rowsRead + nextResult.rows.length, nextOffset) -> nextResult.rows)
        }
      }
    }.prepend(Source.single(firstResult.rows)).mapConcat(identity)

  }

}
