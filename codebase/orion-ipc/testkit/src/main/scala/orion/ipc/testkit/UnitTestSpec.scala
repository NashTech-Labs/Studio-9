package orion.ipc.testkit

import org.scalamock.scalatest.MockFactory
import org.scalatest._

trait UnitTestSpec extends FlatSpec with Matchers with OptionValues
    with BeforeAndAfterAll with BeforeAndAfter with BeforeAndAfterEach
    with Inside with Inspectors with MockFactory {
  def sample(a: Any): String = a.toString
  def makeFloating(a: Int): Double = a.toDouble

  protected object Index {
    private[this] var stream = Stream.from(0)

    def next: Int = {
      val i = stream.headOption.value
      stream = stream.tail
      i
    }
  }

  def source: String = "unit_test"
}
