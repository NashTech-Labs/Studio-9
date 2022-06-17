package argo.rest

import akka.event.NoLogging
import argo.testkit.BaseSpec

class PackageSpec extends BaseSpec {

  "CortexApi package" should {
    "count an action elapsed time" in {
      time(
        "test", "testAction"
      )(NoLogging) shouldBe "test"
    }
  }

}
