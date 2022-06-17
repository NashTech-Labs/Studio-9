package cortex.task.failed

import java.nio.charset.Charset

import cortex.JsonSupport
import cortex.JsonSupport.SnakeJson
import play.api.libs.json._

case class FailedTask(
    errorType:    ErrorType,
    errorMessage: String,
    stackTrace:   String,
    errorCode:    Option[String]
)

object FailedTask {

  implicit val format: Reads[FailedTask] = SnakeJson.reads[FailedTask]

  def parseResult(payload: Array[Byte]): FailedTask = {
    JsonSupport.fromString(new String(payload, Charset.forName("UTF-8")))
  }

}
