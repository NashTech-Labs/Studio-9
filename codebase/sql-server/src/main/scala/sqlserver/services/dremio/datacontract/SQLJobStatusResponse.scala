package sqlserver.services.dremio.datacontract

import sqlserver.utils.json.EnumReadsBuilder
import play.api.libs.json.Reads

sealed trait SQLJobStatusResponse

object SQLJobStatusResponse {

  case object NotSubmitted extends SQLJobStatusResponse
  case object Starting extends SQLJobStatusResponse
  case object Running extends SQLJobStatusResponse
  case object Completed extends SQLJobStatusResponse
  case object Canceled extends SQLJobStatusResponse
  case object Failed extends SQLJobStatusResponse
  case object CancellationRequested extends SQLJobStatusResponse
  case object Enqueued extends SQLJobStatusResponse

  implicit val SQLJobStatusResponseReads: Reads[SQLJobStatusResponse] = EnumReadsBuilder.build(
    {
      case "NOT_SUBMITTED" => NotSubmitted
      case "STARTING" => Starting
      case "RUNNING" => Running
      case "COMPLETED" => Completed
      case "CANCELED" => Canceled
      case "FAILED" => Failed
      case "CANCELLATION_REQUESTED" => CancellationRequested
      case "ENQUEUED" => Enqueued
    },
    "SQL Job Status"
  )

}
