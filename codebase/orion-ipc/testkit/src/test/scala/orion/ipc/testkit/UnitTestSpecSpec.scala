package orion.ipc.testkit

class UnitTestSpecSpec extends UnitTestSpec {

  behavior of "UnitTestSpec Spec"

  it should "return source value" in {
    source shouldBe "unit_test"
  }

  it should "make a sample" in {
    sample(1) shouldBe a[String]
    sample(1) shouldBe "1"
  }

  it should "makeFloating from Int" in {
    makeFloating(1) shouldBe 1.toDouble
  }

  it should "generate the sequential sequence" in {
    Index.next shouldBe 0
    Index.next shouldBe 1
    Index.next shouldBe 2
  }
}
