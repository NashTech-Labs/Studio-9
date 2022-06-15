package baile

import baile.daocommons.filters.{ And, Filter }
import org.mockito.ArgumentMatcher
import org.mockito.integrations.scalatest.IdiomaticMockitoFixture

trait ExtendedBaseSpec extends CommonSpec with IdiomaticMockitoFixture {

  def containsFilter(needle: Filter): ArgumentMatcher[Filter] = new ArgumentMatcher[Filter] {
    override def matches(that: Filter): Boolean = that match {
      case And(x, y) => matches(x) || matches(y)
      case f if f == needle => true
      case _ => false
    }
  }

}
