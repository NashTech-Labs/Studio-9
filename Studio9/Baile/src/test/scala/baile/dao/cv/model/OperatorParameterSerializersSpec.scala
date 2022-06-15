package baile.dao.cv.model

import baile.ExtendedBaseSpec
import baile.dao.OperatorParameterSerializers._
import baile.domain.pipeline._

class OperatorParameterSerializersSpec extends ExtendedBaseSpec {

  private val conditions = Map(
    "int param" -> IntParameterCondition(values = Seq(1), min = Some(1), max = Some(1)),
    "float param" -> FloatParameterCondition(values = Seq(0.2F), min = Some(1.2F), max = Some(3.4F)),
    "string param" -> StringParameterCondition(values = Seq("str1", "str2")),
    "boolean param" -> BooleanParameterCondition(value = true)
  )

  "OperatorParameterSerializers" should {
    "convert INTEGER parameter definition to document and back" in {
      val operatorParameter = OperatorParameter(
        name = "name",
        description = Some("description"),
        multiple = false,
        typeInfo = IntParameterTypeInfo(
          values = Seq(12, 24, 36),
          default = Seq(5),
          min = Some(0),
          max = Some(70),
          step = Some(12)
        ),
        conditions = conditions
      )

      val document = paramsToDocument(operatorParameter)
      val parameterDefinitionEntity = documentToOperatorParameter(document)
      parameterDefinitionEntity shouldBe operatorParameter
    }

    "convert FLOAT parameter definition to document and back" in {
      val operatorParameter = OperatorParameter(
        name = "name",
        description = Some("description"),
        multiple = false,
        typeInfo = FloatParameterTypeInfo(
          values = Seq(1.2F, 4.9F, 6.2F),
          default = Seq(5.1F),
          min = Some(0.2F),
          max = Some(7.4F),
          step = Some(12.7F)
        ),
        conditions = conditions
      )

      val document = paramsToDocument(operatorParameter)
      val parameterDefinitionEntity = documentToOperatorParameter(document)
      parameterDefinitionEntity shouldBe operatorParameter
    }

    "convert STRING parameter definition to document and back" in {
      val operatorParameter = OperatorParameter(
        "name",
        Some("description"),
        false,
        StringParameterTypeInfo(
          Seq("value1", "value2"),
          Seq("default")
        ),
        conditions
      )

      val document = paramsToDocument(operatorParameter)
      val parameterDefinitionEntity = documentToOperatorParameter(document)
      parameterDefinitionEntity shouldBe operatorParameter
    }

    "convert BOOLEAN parameter definition to document and back" in {
      val operatorParameter = OperatorParameter(
        "name",
        Some("description"),
        false,
        BooleanParameterTypeInfo(
          Seq(false)
        ),
        conditions
      )

      val document = paramsToDocument(operatorParameter)
      val parameterDefinitionEntity = documentToOperatorParameter(document)
      parameterDefinitionEntity shouldBe operatorParameter
    }
  }
}
