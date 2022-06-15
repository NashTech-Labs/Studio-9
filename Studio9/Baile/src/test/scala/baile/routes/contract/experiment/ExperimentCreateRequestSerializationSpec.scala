package baile.routes.contract.experiment

import baile.ExtendedBaseSpec
import baile.domain.table.{ ColumnDataType, ColumnVariableType }
import baile.routes.contract.tabular.TabularTrainPipeline
import baile.routes.contract.cv.CVTLTrainStep1Params.NewFEParams
import baile.routes.contract.cv.CommonTrainParams.InputSize
import baile.routes.contract.pipeline.PipelineParams._
import baile.routes.contract.cv.model.CVModelType
import baile.routes.contract.cv.{ CVTLTrainPipeline, CVTLTrainStep1Params, CommonTrainParams, LabelOfInterest }
import baile.routes.contract.pipeline.{
  GenericExperimentPipeline,
  PipelineCoordinates,
  PipelineOutputReference,
  PipelineParams,
  PipelineStep
}
import baile.routes.contract.tabular.model.ModelColumn
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json.Json

class ExperimentCreateRequestSerializationSpec extends ExtendedBaseSpec with TableDrivenPropertyChecks {

  val params = PipelineParams(
    Map(
      "extraS" -> StringParam("value"),
      "extra_F" -> FloatParam(0.6132F),
      "extra_b" -> BooleanParam(true),
      "extraI" -> IntParam(1),
      "seq_extraS" -> StringParams(Seq("value")),
      "seq_extra_F" -> FloatParams(Seq(1, 2, 0.1321F, 0.6132F)),
      "seq_extra_b" -> BooleanParams(Seq(true)),
      "seq_extraI" -> IntParams(Seq(1)),
      "empty_seq" -> EmptySeqParam
    )
  )

  "ExperimentCreateRequest reads" should {
    "deserialize a json properly" in {
      val table = Table(
        ("json", "ExperimentCreateRequest"),
        (
          """
            |{
            |  "name":"name",
            |  "type": "CVTLTrain",
            |  "pipeline": {
            |     "step1": {
            |         "architecture":"SCAE",
            |         "featureExtractorParams": {},
            |         "modelType": {
            |             "tlType":"CLASSIFICATION",
            |             "classifierType":"FCN_2"
            |         },
            |         "input":"input",
            |         "params": {
            |             "extra_b":true,
            |             "seq_extra_b":[true],
            |             "extra_F":0.613200008869171148798479387087213,
            |             "seq_extraI":[1],
            |             "seq_extra_F":[1, 2, 0.1321, 0.613200008869171140938408320472837],
            |             "extraS":"value",
            |             "extraI":1,
            |             "seq_extraS":["value"],
            |             "empty_seq": [],
            |             "null_param": null
            |         },
            |         "trainParams" : {
            |             "inputSize": { "width":20, "height":20 },
            |              "loi" : [
            |                 { "label":"label1", "threshold":0.42 },
            |                 { "label":"label2", "threshold":0.24 }
            |              ],
            |              "defaultVisualThreshold":0.23,
            |              "iouThreshold":0.11,
            |              "featureExtractorLearningRate":0.17,
            |              "modelLearningRate":0.18
            |         }
            |     }
            |  }
            |}
          """.stripMargin,
          ExperimentCreateRequest(
            name = Some("name"),
            description = None,
            pipeline = CVTLTrainPipeline(
              step1 = CVTLTrainStep1Params(
                feParams = NewFEParams(
                  architecture = "SCAE",
                  featureExtractorParams = PipelineParams(Map())
                ),
                modelType = CVModelType.TLConsumer.Classifier("FCN_2"),
                input = "input",
                testInput = None,
                augmentationOptions = None,
                params = params,
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
              step2 = None
            )
          )
        ),
        (
          """
            |{
            |  "name":"name",
            |  "type": "TabularTrain",
            |  "pipeline": {
            |    "samplingWeightColumn":"columnName",
            |    "predictorColumns":[{
            |       "name": "predictorColumn",
            |       "displayName": "predictorColumn",
            |       "dataType": "INTEGER",
            |       "variableType": "CONTINUOUS"
            |     }],
            |     "responseColumn": {
            |       "name": "responseColumn",
            |       "displayName": "responseColumn",
            |       "dataType": "STRING",
            |       "variableType": "CATEGORICAL"
            |     },
            |     "input": "id"
            |  }
            |}
          """.stripMargin,
          ExperimentCreateRequest(
            name = Some("name"),
            description = None,
            pipeline = TabularTrainPipeline(
              samplingWeightColumn = Some("columnName"),
              predictorColumns = List(ModelColumn(
                name = "predictorColumn",
                displayName = "predictorColumn",
                dataType = ColumnDataType.Integer,
                variableType = ColumnVariableType.Continuous
              )),
              responseColumn = ModelColumn(
                name = "responseColumn",
                displayName = "responseColumn",
                dataType = ColumnDataType.String,
                variableType = ColumnVariableType.Categorical
              ),
              input = "id",
              holdOutInput = None,
              outOfTimeInput = None
            )
          )
        ),
        (
          """
            |{
            |  "name":"name",
            |  "type": "GenericExperiment",
            |  "pipeline": {
            |    "steps": [{
            |      "id": "id",
            |      "operator": "operator",
            |      "inputs": {
            |        "name": {
            |          "stepId": "stepId",
            |          "outputIndex": 1
            |        }
            |      },
            |      "params": {
            |        "param": []
            |      },
            |      "coordinates": {
            |        "x": 0,
            |        "y": 0
            |      }
            |    }]
            |  }
            |}
          """.stripMargin,
          ExperimentCreateRequest(
            name = Some("name"),
            description = None,
            pipeline = GenericExperimentPipeline(
              steps = Seq(
                PipelineStep(
                  id = "id",
                  operator = "operator",
                  inputs = Map(
                    "name" -> PipelineOutputReference("stepId", 1)
                  ),
                  params = PipelineParams(Map(
                    "param" -> PipelineParams.EmptySeqParam
                  )),
                  coordinates = Some(PipelineCoordinates(0,0))
                )
              ),
              assets = None
            )
          )
        )
      )

      forAll(table) { (json, experimentCreateRequest) =>
        val parsedExperimentCreateRequest = Json.fromJson[ExperimentCreateRequest](Json.parse(json))
        parsedExperimentCreateRequest.isSuccess shouldBe true
        parsedExperimentCreateRequest.get shouldBe experimentCreateRequest
      }
    }
  }
}

