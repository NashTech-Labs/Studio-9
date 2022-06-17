package gemini

import scala.collection.immutable.List
import scala.util.Random

object RandomGenerators {

  def randomString(length: Int): String =
    Random.alphanumeric.take(length).mkString

  def randomString(): String = randomString(10)

  def randomOf[T](args: T*): T =
    args(Random.nextInt(args.length))

  def randomBoolean(): Boolean = Random.nextBoolean()

  def randomPath(): String =
    List
      .fill(Random.nextInt(5))({
        randomString()
      })
      .mkString("/")

  def randomPath(extension: String): String = randomPath() + "." + extension

  def randomInt(max: Int): Int = Random.nextInt(max)

  def randomInt(min: Int, max: Int): Int = Random.nextInt(max - min) + min

}
