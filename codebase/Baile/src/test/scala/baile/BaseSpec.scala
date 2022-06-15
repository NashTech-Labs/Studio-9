package baile

import akka.http.scaladsl.HttpExt
import baile.daocommons.WithId
import baile.daocommons.filters.{ And, Filter }
import org.mockito.ArgumentMatcher
import org.scalatest.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.argThat

import scala.collection.immutable.List
import scala.util.Random

trait BaseSpec extends CommonSpec with MockitoSugar {

  protected val httpMock: HttpExt = mock[HttpExt]

  def filterContains(needle: Filter): Filter = argThat[Filter](new ArgumentMatcher[Filter] {
    override def matches(that: Filter): Boolean = that match {
      case f if f == needle => true
      case And(x, y) => matches(x) || matches(y)
      case _ => false
    }
  })

  // All of these randomMethods should not be used in favour of RandomGenerators object
  protected def randomString(length: Int): String = {
    Random.alphanumeric.take(length).mkString
  }

  protected def randomString(): String = randomString(10)

  protected def randomOf[T](args: T*): T = {
    args(Random.nextInt(args.length))
  }

  protected def randomBoolean(): Boolean = Random.nextBoolean()

  protected def randomPath(): String = List.fill(Random.nextInt(5))({
    randomString()
  }).mkString("/")

  protected def randomPath(extension: String): String = randomPath() + "." + extension

  protected def randomInt(max: Int): Int = Random.nextInt(max)

  protected def randomInt(min: Int, max: Int): Int = Random.nextInt(max - min) + min

  implicit class WithIdExt[T](self: WithId[T]) {
    def map(f: T => T): WithId[T] =
      WithId(f(self.entity), self.id)
  }

}
