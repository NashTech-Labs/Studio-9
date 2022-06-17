package sqlserver.dao.table

import akka.NotUsed
import akka.actor.{ ActorRef, Scheduler }
import akka.event.LoggingAdapter
import akka.pattern.{ after, ask }
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.Timeout
import cats.data.EitherT
import cats.implicits._
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.util.deparser.{ SelectDeParser, StatementDeParser }
import play.api.libs.json.{ JsNull, JsValue }
import sqlserver.dao.table.TableDataDao.TableDataDaoError
import sqlserver.domain.table.TableRowValue._
import sqlserver.domain.table._
import sqlserver.services.dremio.DremioService._
import sqlserver.services.dremio.datacontract.ColumnTypeNameResponse._
import sqlserver.services.dremio.datacontract.SQLJobStatusResponse._
import sqlserver.services.dremio.datacontract._
import sqlserver.utils.TryExtensions._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class DremioTableDataDao(
  dremioService: ActorRef,
  dremioRetryDelay: FiniteDuration,
  dremioMaxRetries: Int,
  implicit val dremioServiceTimeout: Timeout
)(
  implicit val materializer: Materializer,
  val ec: ExecutionContext,
  val logger: LoggingAdapter,
  val scheduler: Scheduler
) extends TableDataDao {

  private val maxRowsLimit = 500

  override def getTableRowSource(
    query: Select,
    bindings: Map[String, DBValue]
  )(implicit ec: ExecutionContext): Future[Either[TableDataDao.TableDataDaoError, TableQueryResult]] = {

    val result = for {
      modifiedQuery <- EitherT(substituteBindings(query, bindings).toFuture)
      tableQueryResult <- EitherT(buildTableQueryResult(modifiedQuery))
    } yield tableQueryResult

    result.value
  }

  private def buildTableQueryResult(query: String): Future[Either[TableDataDaoError, TableQueryResult]] = {

    def submitJob(): Future[String] = (dremioService ? SubmitSqlJob(query)).mapTo[SQLJobSubmittedResponse].map(_.id)

    def buildColumnInfo(schema: Seq[DremioColumnResponse]): Try[Seq[Column]] = Try {

      def convertColumnDataType(columnType: ColumnTypeNameResponse): ColumnDataType =
        columnType match {
          case Date | IntervalYear | IntervalDay | Time => ColumnDataType.String
          case Varchar => ColumnDataType.String
          // TODO Check json format which Dremio uses to represent values of this type and implement this
          case List | Struct | Union | Varbinary => ???
          case Other => ColumnDataType.String
          case Boolean => ColumnDataType.Boolean
          case Integer => ColumnDataType.Integer
          case Double | Float | Decimal => ColumnDataType.Double
          case BigInt => ColumnDataType.Long
          case Timestamp => ColumnDataType.Timestamp
        }

      schema.map { dremioColumn =>
        Column(
          dremioColumn.name,
          convertColumnDataType(dremioColumn.`type`.name)
        )
      }
    }

    def parseTableRow(results: Map[String, JsValue], columns: Seq[Column]): Try[TableRow] = Try {
      val values = columns.map { columnInfo =>
        val resultValue = results(columnInfo.name)
        resultValue match {
          case JsNull =>
            NullValue
          case jsValue =>
            columnInfo.dataType match {
              case ColumnDataType.String => StringValue(jsValue.as[String])
              case ColumnDataType.Boolean => BooleanValue(jsValue.as[Boolean])
              case ColumnDataType.Integer => IntegerValue(jsValue.as[Int])
              case ColumnDataType.Double => DoubleValue(jsValue.as[Double])
              case ColumnDataType.Long => LongValue(jsValue.as[Long])
              case ColumnDataType.Timestamp => TimestampValue(jsValue.as[String])
            }
        }
      }

      TableRow(values)
    }

    val result = for {
      jobId <- EitherT.right[TableDataDaoError](submitJob())
      firstResult <- EitherT(pollJobResults(jobId))
      columns <- EitherT.right[TableDataDaoError](buildColumnInfo(firstResult.schema).toFuture)
      source = createResultsSource(jobId, firstResult).map(parseTableRow(_, columns).get)
    } yield TableQueryResult(source, columns, Some(firstResult.rowCount))

    result.value
  }

  private def pollJobResults(jobId: String): Future[Either[TableDataDaoError, SQLJobResultResponse]] = {

    def poll(retriesLeft: Int): Future[Either[TableDataDaoError, SQLJobResultResponse]] =
      for {
        job <- (dremioService ? GetJob(jobId)).mapTo[SQLJobResponse]
        result <- job.jobState match {
          case Enqueued | Starting | Running =>
            if (retriesLeft > 0) {
              after(dremioRetryDelay, scheduler)(poll(retriesLeft - 1))
            } else {
              Future.failed(new RuntimeException(s"Job was not ready after $dremioMaxRetries retries"))
            }
          case Completed =>
            (dremioService ? GetJobResultPage(
              jobId = jobId,
              offset = 0,
              limit = maxRowsLimit
            )).mapTo[SQLJobResultResponse].map(_.asRight)
          case Failed =>
            Future.successful(
              TableDataDaoError.EngineError(new RuntimeException(job.errorMessage.getOrElse(""))).asLeft
            )
          case unexpected =>
            Future.failed(new RuntimeException(s"Unexpected status $unexpected"))
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

    Source
      .unfoldAsync((firstResult.rows.length, 0)) {
        case (rowsRead, offset) =>
          if (rowsRead == totalRows) {
            Future.successful(None)
          } else {
            val nextOffset = offset + maxRowsLimit
            readMoreResults(nextOffset).map { nextResult =>
              Some((rowsRead + nextResult.rows.length, nextOffset) -> nextResult.rows)
            }
          }
      }
      .prepend(Source.single(firstResult.rows))
      .mapConcat(identity)

  }

  private[table] def substituteBindings(
    query: Select,
    bindings: Map[String, DBValue]
  ): Try[Either[TableDataDaoError.ParameterNotFound, String]] =
    Try {
      val buffer = new java.lang.StringBuilder()
      val bindingsReplacer = new BindingsReplacer(buffer, bindings)
      val selectDeparser = new SelectDeParser(bindingsReplacer, buffer)
      val statementDeparser = new StatementDeParser(bindingsReplacer, selectDeparser, buffer)
      query.accept(statementDeparser)
      buffer.toString.asRight[TableDataDaoError.ParameterNotFound]
    } recover {
      case ParameterNotFoundException(parameterName) =>
        TableDataDaoError.ParameterNotFound(parameterName).asLeft
    }

}
