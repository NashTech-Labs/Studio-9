package cortex.api.job.pipeline
import cortex.api.job.common.{ ClassReference, ConfusionMatrix, ConfusionMatrixCell }

object Sample {

  val pipelineStepRequest = PipelineStepRequest(
    stepId = "step1",
    operator = Some(
      ClassReference(
        Some("/pipelineOperator/"),
        moduleName = "loaders",
        className = "load_Album"
      )
    ),
    inputs = Map(
      "foo" -> PipelineOutputReference(
        stepId = "step1",
        outputIndex = 22
      )
    ),
    params = Map(
      "foo" -> PipelineParam(PipelineParam.Param.StringParam("param1")),
      "bar" -> PipelineParam(PipelineParam.Param.IntParam(0)),
      "foo1" -> PipelineParam(PipelineParam.Param.StringParams(StringSequenceParams(Seq("param1", "param2")))),
      "bar1" -> PipelineParam(PipelineParam.Param.BooleanParams(BooleanSequenceParams(Seq(true, false))))

    )
  )

  val pipelineRunRequest = PipelineRunRequest(
    pipelineStepsRequest = Seq(pipelineStepRequest),
    baileAuthToken = "8e8e2879-9e05-49f4-a186-06be18429b2d"
  )

  val pipelineOutputReference = PipelineOutputReference(
    stepId = "step2",
    outputIndex = 21
  )

  val trackedAssetReference = TrackedAssetReference(
    assetId = "asset1",
    assetType = AssetType.CvModel
    )

  val initialPredictConfusionCells = Seq(
    ConfusionMatrixCell(Some(0), Some(0), 2),
    ConfusionMatrixCell(Some(0), Some(1), 1),
    ConfusionMatrixCell(Some(1), Some(1), 1),
    ConfusionMatrixCell(Some(1), Some(0), 2)
  )

  val labels = Seq("label1", "label2")

  val confusionMatrix = ConfusionMatrix(initialPredictConfusionCells, labels)

  val simpleSummary = SimpleSummary(
    values = Map(
      "foo" -> PipelineValue(PipelineValue.Value.StringValue("value1")),
      "bar" -> PipelineValue(PipelineValue.Value.IntValue(0))
    )
  )

  val operatorApplicationSummary = OperatorApplicationSummary(
    OperatorApplicationSummary.Summary.ConfusionMatrix(confusionMatrix)
  )

  val pipelineStepGeneralResponse = PipelineStepGeneralResponse(
    stepId = "step1",
    trackedAssetReferences = Seq(trackedAssetReference),
    summaries = Seq(operatorApplicationSummary),
    outputValues = Map(
      1 -> PipelineValue(PipelineValue.Value.IntValue(0)),
      2 -> PipelineValue(PipelineValue.Value.IntValue(0))
    ),
    stepExecutionTime = 75900L
  )

  val pipelineStepFailureResponse = PipelineStepFailureResponse(
    pipelineStepGeneralResponse = Some(pipelineStepGeneralResponse),
    errorMessage = Some("Error occurred at step1")
  )

  val pipelineStepResponse = PipelineStepResponse(
  PipelineStepResponse.Response.PipelineStepGeneralResponse(pipelineStepGeneralResponse)
  )

  val pipelineRunResponse = PipelineRunResponse(
    pipelineStepsResponse = Seq(pipelineStepResponse)
  )



}
