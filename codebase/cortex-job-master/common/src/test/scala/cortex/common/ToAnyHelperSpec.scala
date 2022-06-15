package cortex.common

import org.scalatest.{ FlatSpec, Matchers }

// scalastyle:off null
class ToAnyHelperSpec extends FlatSpec with Matchers {

  behavior of "Any helpers"

  it should "check isNullOrEmpty for null values" in {
    Option(null).isEmpty shouldBe true
  }

  it should "convert null String to empty String" in {
    val str: String = null
    str.toStr shouldBe ""
  }

  it should "convert null Object to empty String" in {
    val obj: Object = null
    obj.toStr shouldBe ""
  }
}
