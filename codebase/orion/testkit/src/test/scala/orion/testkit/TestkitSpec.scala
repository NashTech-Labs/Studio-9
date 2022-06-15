package orion.testkit

class TestkitSpec extends BaseSpec {

  "testkit" should {
    "read resource artifact as list of strings" in {
      resourceAsLines("/test.json") should not be empty
    }
  }

}
