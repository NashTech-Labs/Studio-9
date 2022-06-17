package baile.routes.contract.cv

import baile.BaseSpec
import baile.routes.contract.pipeline.PipelineParams
import play.api.libs.json._

class CVTLTrainStep1ParamsTest extends BaseSpec {
  "CVTLTrainStep1ParamsTest reads" should {
    "parse json with architecture and featureExtractorParams" in {

      val parsed = Json.parse(
        """{
          |  "architecture": "foo",
          |  "featureExtractorParams": {"bar": "do"},
          |  "modelType": {
          |    "tlType": "CLASSIFICATION",
          |    "classifierType": "foo"
          |  },
          |  "input": "inputId",
          |  "augmentationOptions": null,
          |  "params": {"bar": "do"}
          |}""".stripMargin
      ).as[CVTLTrainStep1Params]

      parsed shouldBe an [CVTLTrainStep1Params]
      parsed.feParams shouldBe a [CVTLTrainStep1Params.NewFEParams]
      parsed.feParams.asInstanceOf[CVTLTrainStep1Params.NewFEParams]
        .featureExtractorParams.value("bar") shouldBe PipelineParams.StringParam("do")
    }

    "parse json with existing FE reference" in {

      val parsed = Json.parse(
        """{
          |  "featureExtractorModelId": "feModel",
          |  "tuneFeatureExtractor": false,
          |  "modelType": {
          |    "tlType": "CLASSIFICATION",
          |    "classifierType": "foo"
          |  },
          |  "input": "inputId",
          |  "augmentationOptions": null,
          |  "params": {"bar": "do"}
          |}""".stripMargin
      ).as[CVTLTrainStep1Params]

      parsed shouldBe an [CVTLTrainStep1Params]
      parsed.feParams shouldBe a [CVTLTrainStep1Params.ExistingFEParams]
    }

    "fail on incorrect FE fields combination" in {

      val result = Json.parse(
        """{
          |  "architecture": "foo",
          |  "tuneFeatureExtractor": false,
          |  "modelType": {
          |    "tlType": "CLASSIFICATION",
          |    "classifierType": "foo"
          |  },
          |  "input": "inputId",
          |  "augmentationOptions": null,
          |  "params": {"bar": "do"}
          |}""".stripMargin
      ).validate[CVTLTrainStep1Params]

      result shouldBe an [JsError]
    }
  }
}
