package cortex.api.gemini

import play.api.libs.json._

sealed trait SessionStatus

object SessionStatus {

  case object Submitted extends SessionStatus

  case object Queued extends SessionStatus

  case object Running extends SessionStatus

  case object Completed extends SessionStatus

  case object Failed extends SessionStatus

  implicit val format: Format[SessionStatus] = new Format[SessionStatus] {
    override def reads(json: JsValue): JsResult[SessionStatus] = json match {
      case JsString(string) =>
        string match {
          case "SUBMITTED" => JsSuccess(SessionStatus.Submitted)
          case "QUEUED"    => JsSuccess(SessionStatus.Queued)
          case "RUNNING"   => JsSuccess(SessionStatus.Running)
          case "COMPLETED" => JsSuccess(SessionStatus.Completed)
          case "FAILED"    => JsSuccess(SessionStatus.Failed)
          case _           => JsError(s"Unknown mode '$string'")
        }
      case _ =>
        JsError("Expected string")
    }

    override def writes(value: SessionStatus): JsValue = value match {
      case SessionStatus.Submitted => JsString("SUBMITTED")
      case SessionStatus.Queued    => JsString("QUEUED")
      case SessionStatus.Running   => JsString("RUNNING")
      case SessionStatus.Completed => JsString("COMPLETED")
      case SessionStatus.Failed    => JsString("FAILED")
    }
  }

}
