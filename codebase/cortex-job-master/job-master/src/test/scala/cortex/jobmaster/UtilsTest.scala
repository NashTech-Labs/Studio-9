package cortex.jobmaster

import cortex.common.Utils._
import org.scalatest.{ FlatSpec, Matchers }

class UtilsTest extends FlatSpec with Matchers {

  "cut base path" should "cut base part of some fs path leaving only a relative part" in {
    val basePath = "base/path"
    val target = "base/path/some/relative/part/1.jpg"
    val relativePart = cutBasePath(basePath, target)
    relativePart shouldBe "some/relative/part/1.jpg"
  }

  "cut base path" should "cut base part of some fs path leaving only a relative part if the base path ends with '/'" in {
    val basePath = "base/"
    val target = "base/some/relative/part/1.jpg"
    val relativePart = cutBasePath(basePath, target)
    relativePart shouldBe "some/relative/part/1.jpg"
  }

  "cut base path" should "throw an exception whether target doesn't contain a base path part" in {
    val basePath = "base/path"
    val target = "some/relative/part/1.jpg"
    intercept[IllegalArgumentException](cutBasePath(basePath, target))
  }

  "cut base path" should "throw an exception whether target contains a part of a base path" in {
    val basePath = "foo"
    val target = "footer/bar/part/1.jpg"
    intercept[IllegalArgumentException](cutBasePath(basePath, target))
  }
}
