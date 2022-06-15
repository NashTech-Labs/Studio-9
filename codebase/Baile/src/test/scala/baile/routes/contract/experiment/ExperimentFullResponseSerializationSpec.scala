package baile.routes.contract.experiment

import java.time.Instant

import baile.ExtendedBaseSpec
import baile.domain.experiment.ExperimentStatus
import baile.routes.contract.cv.CVTLTrainStep1Params.ExistingFEParams
import baile.routes.contract.cv.CommonTrainParams.InputSize
import baile.routes.contract.pipeline.PipelineParams._
import baile.routes.contract.cv.model.CVModelType
import baile.routes.contract.cv.{
  CVTLTrainPipeline,
  CVTLTrainStep1Params,
  CVTLTrainStep2Params,
  CommonTrainParams,
  LabelOfInterest
}
import baile.routes.contract.pipeline.PipelineParams
import play.api.libs.json.Json

class ExperimentFullResponseSerializationSpec extends ExtendedBaseSpec {

  "ExperimentFullResponse writes" should {
    "serialize an object properly" in {
      val instant = Instant.parse("2019-03-27T01:26:32.426Z")
      val expectedJson = Json.parse(
        """
          |{
          |  "id":"id",
          |  "ownerId":"id",
          |  "name":"name",
          |  "created":"2019-03-27T01:26:32.426Z",
          |  "updated":"2019-03-27T01:26:32.426Z",
          |  "status":"COMPLETED",
          |  "type":"CVTLTrain",
          |  "pipeline": {
          |     "step1": {
          |         "featureExtractorModelId":"id",
          |         "modelType": {
          |             "tlType":"CLASSIFICATION",
          |             "classifierType":"FCN_2"
          |         },
          |         "input":"input",
          |         "params": {
          |             "extra_b":true,
          |             "seq_extra_b":[true],
          |             "extra_F":0.1321,
          |             "seq_extraI":[1],
          |             "seq_extra_F":[0.1321],
          |             "extraS":"value",
          |             "extraI":1,
          |             "seq_extraS":["value"],
          |             "empty_seq": []
          |         },
          |         "trainParams" : {
          |             "inputSize": { "width":20, "height":20 },
          |              "loi" : [
          |                 { "label":"label1", "threshold":0.42 },
          |                 { "label":"label2", "threshold":0.24 }
          |              ],
          |              "defaultVisualThreshold":0.23000000417232513,
          |              "iouThreshold":0.10999999940395355,
          |              "featureExtractorLearningRate":0.17,
          |              "modelLearningRate":0.18
          |         }
          |     },
          |     "step2": {
          |         "modelType": {
          |             "tlType":"CLASSIFICATION",
          |             "classifierType":"FCN_2"
          |         },
          |         "params":{},
          |         "input":"input",
          |         "trainParams":{}
          |     }
          |  }
          |}
        """.stripMargin)

      val cVModelResponse = ExperimentFullResponse(
        _base = ExperimentResponse(
          id = "id",
          ownerId = "id",
          name = "name",
          created = instant,
          updated = instant,
          status = ExperimentStatus.Completed,
          description = None,
          `type` = ExperimentType.CVTLTrain
        ),
        pipeline = CVTLTrainPipeline(
          step1 = CVTLTrainStep1Params(
            feParams = ExistingFEParams(
              featureExtractorModelId = "id",
              tuneFeatureExtractor = None
            ),
            modelType = CVModelType.TLConsumer.Classifier("FCN_2"),
            input = "input",
            testInput = None,
            augmentationOptions = None,
            params = PipelineParams(
              Map(
                "extraS" -> StringParam("value"),
                "extra_F" -> FloatParam(0.1321F),
                "extra_b"-> BooleanParam(true),
                "extraI"-> IntParam(1),
                "seq_extraS" -> StringParams(Seq("value")),
                "seq_extra_F" -> FloatParams(Seq(0.1321F)),
                "seq_extra_b"-> BooleanParams(Seq(true)),
                "seq_extraI"-> IntParams(Seq(1)),
                "empty_seq" -> EmptySeqParam
              )
            ),
            trainParams = Some(CommonTrainParams(
              inputSize = Some(InputSize(20, 20)),
              loi = Some(Seq(
                LabelOfInterest("label1", 0.42),
                LabelOfInterest("label2", 0.24)
              )),
              defaultVisualThreshold = Some(0.23f),
              iouThreshold = Some(0.11f),
              featureExtractorLearningRate = Some(0.17d),
              modelLearningRate = Some(0.18d)
            ))
          ),
          step2 = Some(
            CVTLTrainStep2Params(
              tuneFeatureExtractor = None,
              modelType = CVModelType.TLConsumer.Classifier("FCN_2"),
              params = PipelineParams(Map()),
              input = "input",
              testInput = None,
              augmentationOptions = None,
              trainParams = Some(CommonTrainParams(None, None, None, None, None, None))
            )
          )
        ),
        result = None
      )

      Json.toJson(cVModelResponse) shouldBe expectedJson
    }
  }
}

