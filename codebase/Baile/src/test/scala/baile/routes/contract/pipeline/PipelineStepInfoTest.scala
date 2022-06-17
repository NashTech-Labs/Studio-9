package baile.routes.contract.pipeline

import baile.BaseSpec
import play.api.libs.json._

class PipelineStepInfoTest extends BaseSpec {

  "PipelineStepInfoFormat" should {

    "serialize and deserialize json properly" in {
      val json = JsObject(Map(
        "id" -> JsString("step-123"),
        "operator" -> JsString("operator-123"),
        "inputs" -> JsObject(Map(
          "album" -> JsObject(Map(
            "stepId" -> JsString("step-99"),
            "outputIndex" -> JsNumber(2)
          ))
        )),
        "params" -> JsObject(Map(
          "name" -> JsString("my-album")
        )),
        "coordinates" -> JsObject(Map(
          "x" -> JsNumber(10),
          "y" -> JsNumber(20),
        )),
        "pipelineParameters" -> JsObject(Map(
          "description" -> JsString("Description of the output album"),
          "size" -> JsString("Size of the output album")
        ))
      ))
      val pipelineStepInfo = PipelineStepInfo(
        PipelineStep(
          id = "step-123",
          operator = "operator-123",
          inputs = Map(
            "album" -> PipelineOutputReference(
              stepId = "step-99",
              outputIndex = 2
            )
          ),
          params = PipelineParams(Map(
            "name" -> PipelineParams.StringParam("my-album")
          )),
          coordinates = Some(PipelineCoordinates(
            x = 10,
            y = 20
          ))
        ),
        Map(
          "description"-> "Description of the output album",
          "size" -> "Size of the output album"
        )
      )

      json.as[PipelineStepInfo] shouldBe pipelineStepInfo
      Json.toJson(pipelineStepInfo) shouldBe json
    }
  }
}
