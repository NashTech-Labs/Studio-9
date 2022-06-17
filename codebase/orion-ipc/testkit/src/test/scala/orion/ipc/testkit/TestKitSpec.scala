package orion.ipc.testkit

class TestKitSpec extends UnitTestSpec {

  behavior of "testkit"

  it should "read resource artifact as list of strings" in {
    resourceAsLines("/test.json") should not be empty
  }
}
