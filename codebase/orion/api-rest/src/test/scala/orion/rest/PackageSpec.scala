package orion.rest

import akka.event.NoLogging
import orion.testkit.BaseSpec

class PackageSpec extends BaseSpec {

  "CortexApi package" should {
    "count an action elapsed time" in {
      time(
        "test", "testAction"
      )(NoLogging) shouldBe "test"
    }
  }

}
