package cortex.testkit

import java.util.Date

import org.scalactic.Equality
import org.scalatest._

trait BaseSpec extends WordSpecLike with Matchers with CustomEqualitites

trait CustomEqualitites {

  implicit val dateEquality: Equality[Date] = {
    import java.time.temporal.ChronoUnit
    new Equality[Date] {
      def areEqual(a: Date, b: Any): Boolean =
        b match {
          // Ignore milliseconds precision since we might compare dates converted from ISO-8601 format.
          case b: Date => b.toInstant.truncatedTo(ChronoUnit.SECONDS) == a.toInstant.truncatedTo(ChronoUnit.SECONDS)
          case _       => false
        }
    }
  }
}