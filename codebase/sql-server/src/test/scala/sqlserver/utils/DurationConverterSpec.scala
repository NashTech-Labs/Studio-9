package sqlserver.utils

import sqlserver.BaseSpec

import scala.concurrent.duration._
import java.time.Duration

class DurationConverterSpec extends BaseSpec {

  "DurationConverter#toScalaFiniteDuration" should {
    "convert duration from java to scala properly" in {
      DurationConverter.toScalaFiniteDuration(Duration.ofMillis(10)) shouldBe
        (10 * 1000 * 1000).nanoseconds
    }
  }
}
