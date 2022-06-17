package cortex.task.failed

import cortex.JsonSupport.SnakeJson
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class FailedTaskSerializationTest extends FlatSpec {

  "FailedTask" should "be deserialized from json string properly" in {
    val serializedResult =
      """{
        |  "error_type": "system",
        |  "error_message": "Input contains NaN, infinity or a value too large for dtype('float64').",
        |  "stack_trace": "Traceback (most recent call last):"
        |}
      """.stripMargin

    val expectedResult = FailedTask(
      errorType    = ErrorType.SystemError,
      errorCode    = None,
      errorMessage = "Input contains NaN, infinity or a value too large for dtype('float64').",
      stackTrace   = "Traceback (most recent call last):"
    )

    SnakeJson.parse(serializedResult).as[FailedTask] shouldBe expectedResult
  }
}
