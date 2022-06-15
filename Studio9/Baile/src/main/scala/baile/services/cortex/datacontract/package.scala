package baile.services.cortex

import baile.domain.job.CortexJobStatus
import baile.domain.job.CortexJobStatus._
import baile.utils.json.EnumFormatBuilder
import play.api.libs.json._

package object datacontract {

  implicit val CortexJobStatusFormat: Format[CortexJobStatus] = EnumFormatBuilder.build(
    {
      case "submitted" => Submitted
      case "queued" => Queued
      case "running" => Running
      case "completed" => Completed
      case "cancelled" => Cancelled
      case "failed" => Failed
    },
    {
      case Submitted => "submitted"
      case Queued => "queued"
      case Running => "running"
      case Completed => "completed"
      case Cancelled => "cancelled"
      case Failed => "failed"
    },
    "cortex job status"
  )

}
