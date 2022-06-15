package baile.routes.contract.pipeline.operator

import baile.ExtendedBaseSpec
import baile.domain.asset.AssetType
import baile.routes.contract.pipeline.operator.ParameterConditionResponse._
import baile.routes.contract.pipeline.operator.ParameterDefinitionResponse._
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json.Json

class ParameterDefinitionResponseSerializationSpec extends ExtendedBaseSpec with TableDrivenPropertyChecks {

  "ParameterDefinitionResponse writes" should {
    "serialize an object properly" in {

      val table = Table(
        ("json", "ParameterDefinitionResponse"),
        (
          """
            |{
            |  "name":"name",
            |  "description":"description",
            |  "multiple":true,
            |  "defaults":[false],
            |  "conditions":{
            |     "othername":{
            |        "value":true
            |     }
            |  },
            |  "type":"boolean"
            |}
          """.stripMargin,
          BooleanParameterDefinitionResponse(
            name = "name",
            description = Some("description"),
            multiple = true,
            conditions = Some(Map(
              "othername" -> BooleanParameterConditionResponse(true)
            )),
            defaults = Some(Seq(false))
          )
        ),
        (
          """
            |{
            |  "name":"name",
            |  "description":"description",
            |  "multiple":false,
            |  "options":[1,2,3],
            |  "defaults":[2],
            |  "conditions":{
            |     "othername":{
            |        "values":[2],
            |        "min":1,
            |        "max":3
            |     }
            |  },
            |  "min":1,
            |  "max":3,
            |  "step":1,
            |  "type":"int"
            |}
          """.stripMargin,
          IntParameterDefinitionResponse(
            name = "name",
            description = Some("description"),
            multiple = false,
            conditions = Some(Map(
              "othername" -> IntParameterConditionResponse(
                values = Some(Seq(2)),
                min = Some(1),
                max = Some(3)
              )
            )),
            options = Some(Seq(1,2,3)),
            defaults = Some(Seq(2)),
            min = Some(1),
            max = Some(3),
            step = Some(1)
          )
        ),
        (
          """
            |{
            |  "name":"name",
            |  "description":"description",
            |  "multiple":false,
            |  "options":["s1", "s2"],
            |  "defaults":["s2"],
            |  "conditions":{
            |     "othername":{
            |        "values":["str1"]
            |     }
            |  },
            |  "type":"string"
            |}
          """.stripMargin,
          StringParameterDefinitionResponse(
            name = "name",
            description = Some("description"),
            multiple = false,
            conditions = Some(Map(
              "othername" -> StringParameterConditionResponse(Seq("str1"))
            )),
            options = Seq("s1", "s2"),
            defaults = Some(Seq("s2"))
          )
        ),
        (
          """
            |{
            |  "name":"name",
            |  "description":"description",
            |  "multiple":false,
            |  "assetType":"ALBUM",
            |  "type":"assetReference"
            |}
          """.stripMargin,
          AssetParameterDefinitionResponse(
            name = "name",
            description = Some("description"),
            multiple = false,
            conditions = None,
            assetType = AssetType.Album
          )
        ),
        (
          """
            |{
            |  "name":"name",
            |  "description":"description",
            |  "multiple":false,
            |  "options":[1,2.4,3],
            |  "defaults":[2.4],
            |  "conditions":{
            |     "othername":{
            |        "values":[0.22],
            |        "min":1,
            |        "max":3
            |     }
            |  },
            |  "min":0.1,
            |  "max":-0.0000000001,
            |  "step":1,
            |  "type":"float"
            |}
          """.stripMargin,
          FloatParameterDefinitionResponse(
            name = "name",
            description = Some("description"),
            multiple = false,
            conditions = Some(Map(
              "othername" -> FloatParameterConditionResponse(
                values = Some(Seq(0.22f)),
                min = Some(1),
                max = Some(3)
              )
            )),
            options = Some(Seq(1,2.4f,3)),
            defaults = Some(Seq(2.4f)),
            min = Some(0.1f),
            max = Some(-0.0000000001f),
            step = Some(1)
          )
        )
      )

      forAll(table) { (json, definitionResponse) =>
        val expectedJson = Json.parse(json)
        Json.toJson(definitionResponse) shouldBe expectedJson
      }
    }
  }
}

