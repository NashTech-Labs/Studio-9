package taurus.common

import org.scalatest.{ Matchers, WordSpecLike }

// scalastyle:off null
class ToAnyHelperSpec extends WordSpecLike with Matchers {

  "Any helpers" should {
    "check isNullOrEmpty for null values" in {
      Option(null).isEmpty shouldBe true
    }

    "convert null String to empty String" in {
      val str: String = null
      str.toStr shouldBe ""
    }

    "convert null Object to empty String" in {
      val obj: Object = null
      obj.toStr shouldBe ""
    }
  }

}