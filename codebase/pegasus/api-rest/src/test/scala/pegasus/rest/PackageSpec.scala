package pegasus.rest

import akka.event.NoLogging
import pegasus.testkit.BaseSpec

class PackageSpec extends BaseSpec {

  "CortexApi package" should {
    "count an action elapsed time" in {
      time(
        "test", "testAction"
      )(NoLogging) shouldBe "test"
    }
  }

}
