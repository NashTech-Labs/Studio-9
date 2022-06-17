package cortex.task.failed

import play.api.libs.json._

sealed trait ErrorType {
  val errorType: String
}

object ErrorType {

  case object SystemError extends ErrorType {
    override val errorType: String = "system"
  }

  case object UserError extends ErrorType {
    override val errorType: String = "user"
  }

  implicit val format: Reads[ErrorType] = new Reads[ErrorType] {
    override def reads(json: JsValue): JsResult[ErrorType] = {
      json match {
        case JsString(SystemError.errorType) => JsSuccess(SystemError)
        case JsString(UserError.errorType)   => JsSuccess(UserError)
        case _                               => JsError(s"Invalid ErrorType: $json")
      }
    }
  }

}
