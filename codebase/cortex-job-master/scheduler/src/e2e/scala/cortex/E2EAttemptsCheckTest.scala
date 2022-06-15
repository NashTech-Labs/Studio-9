package cortex

import cortex.testkit.{AttemptsCheckUtil, FutureTestUtils, WithS3AndMesosScheduler}
import org.scalatest.FlatSpec

class E2EAttemptsCheckTest extends FlatSpec
    with FutureTestUtils
    with AttemptsCheckUtil
    with WithS3AndMesosScheduler {

  "AttemptsCheck" should "succeed on 3rd try while running on mesos scheduler" in {
    runSucceedTask(taskScheduler).await()
  }

  it should "fail on mesos scheduler" in {
    intercept[Exception] {
      runFailedTask(taskScheduler).await()
    }
  }
}
