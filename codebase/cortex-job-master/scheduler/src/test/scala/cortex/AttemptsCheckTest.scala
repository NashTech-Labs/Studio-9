package cortex

import cortex.testkit.{ AttemptsCheckUtil, FutureTestUtils, WithS3AndLocalScheduler }
import org.scalatest.FlatSpec

import scala.concurrent.ExecutionException

class AttemptsCheckTest extends FlatSpec
  with FutureTestUtils
  with AttemptsCheckUtil
  with WithS3AndLocalScheduler {

  "AttemptsCheck" should "run on local task scheduler" in {
    runSucceedTask(taskScheduler).await()
  }

  it should "fail on local task scheduler" in {
    intercept[ExecutionException] {
      runFailedTask(taskScheduler).await()
    }
  }
}
