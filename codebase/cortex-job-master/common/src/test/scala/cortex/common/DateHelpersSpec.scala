package cortex.common

import org.joda.time.DateTime
import org.scalatest.{ FlatSpec, Matchers }

// scalastyle:off null
class DateHelpersSpec extends FlatSpec with Matchers {

  behavior of "Date helpers"

  it should "return the current Date" in {
    now.getTime should be < System.currentTimeMillis()
  }

  it should "return the current DateTime" in {
    nowDateTime.getMillis should be < System.currentTimeMillis()
  }

  it should "convert null DateTime to empty string" in {
    val dateTime: DateTime = null
    dateTime.toUtcIso shouldBe ""
  }

  it should "convert DateTime to UTC ISO string" in {
    val dateStr = "2016-07-30T00:00:00.000Z"
    val dateTime = DateTime.parse(dateStr)
    dateTime.toUtcIso shouldBe dateStr
  }

  it should "convert null Date to empty string" in {
    val dateTime: DateTime = null
    dateTime.toUtcIso shouldBe ""
  }

  it should "convert Date to UTC ISO string" in {
    val dateStr = "2016-07-30T00:00:00.000Z"
    val date = DateTime.parse(dateStr).toDate
    date.toUtcIso shouldBe dateStr
  }
}
