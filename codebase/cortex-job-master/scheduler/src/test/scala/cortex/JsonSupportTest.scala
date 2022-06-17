package cortex

import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import play.api.libs.json.{ Json, OFormat }

class JsonSupportTest extends FlatSpec {
  case class TestCaseClass(someName: String, someOtherName: String)

  private val defaultSampleClassFormat: OFormat[TestCaseClass] = Json.format[TestCaseClass]
  private val snakeSampleClassFormat: OFormat[TestCaseClass] = JsonSupport.SnakeJson.format[TestCaseClass]

  "Default Json format" should "serialize object to json string" in {
    val obj = TestCaseClass("name", "another_name")
    Json.toJson(obj)(defaultSampleClassFormat).toString() shouldBe """{"someName":"name","someOtherName":"another_name"}"""
  }

  "Configured Json" should "serialize object to snake json string" in {
    val obj = TestCaseClass("name", "another_name")
    Json.toJson(obj)(snakeSampleClassFormat).toString() shouldBe """{"some_name":"name","some_other_name":"another_name"}"""
  }

  "Default Json format" should "deserialize object from json string" in {
    val jsonString = """{"someName":"name","someOtherName":"another_name"}"""
    val obj = Json.parse(jsonString).as[TestCaseClass](defaultSampleClassFormat)
    obj.someName shouldBe "name"
    obj.someOtherName shouldBe "another_name"
  }

  "Configured Json" should "deserialize object from snake json string" in {
    val snakeJsonString = """{"some_name":"name","some_other_name":"another_name"}"""
    val obj = Json.parse(snakeJsonString).as[TestCaseClass](snakeSampleClassFormat)
    obj.someName shouldBe "name"
    obj.someOtherName shouldBe "another_name"
  }
}
