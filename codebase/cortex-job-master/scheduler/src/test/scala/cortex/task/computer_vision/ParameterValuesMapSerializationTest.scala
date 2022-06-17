package cortex.task.computer_vision

import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import cortex.JsonSupport._

class ParameterValuesMapSerializationTest extends FlatSpec {

  "json serializer" should "serialize map of ParameterValue properly" in {
    val expectedParameterValuesMap = """{"string_seq":["string value 1"],"float":1.5,"float_seq":[1.5],""" +
      """"string":"string value","boolean":true,"int":1,"int_seq":[1],"boolean_seq":[true]}"""
    val parameterValuesMap =
      Map(
        "string" -> ParameterValue.StringValue("string value"),
        "int" -> ParameterValue.IntValue(1),
        "float" -> ParameterValue.FloatValue(1.5F),
        "boolean" -> ParameterValue.BooleanValue(true),
        "string_seq" -> ParameterValue.StringValues(Seq("string value 1")),
        "int_seq" -> ParameterValue.IntValues(Seq(1)),
        "float_seq" -> ParameterValue.FloatValues(Seq(1.5F)),
        "boolean_seq" -> ParameterValue.BooleanValues(Seq(true))
      )

    val serializedParameterValuesMap = SnakeJson.toJson(parameterValuesMap).toString()

    serializedParameterValuesMap shouldBe expectedParameterValuesMap
  }
}
