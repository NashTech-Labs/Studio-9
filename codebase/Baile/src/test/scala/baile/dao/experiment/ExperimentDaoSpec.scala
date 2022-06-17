package baile.dao.experiment

import java.time.Instant
import java.util.UUID

import baile.BaseSpec
import baile.dao.cv.model.CVModelPipelineSerializer
import baile.dao.pipeline.GenericExperimentPipelineSerializer
import baile.dao.tabular.model.TabularModelPipelineSerializer
import baile.domain.asset.{ AssetReference, AssetType }
import baile.domain.common.ConfusionMatrixCell
import baile.domain.pipeline.PipelineParams._
import baile.domain.cv.{ CommonTrainParams, LabelOfInterest }
import baile.domain.cv.CommonTrainParams.InputSize
import baile.domain.cv.model._
import baile.domain.cv.pipeline._
import baile.domain.cv.result.{ CVTLTrainResult, CVTLTrainStepResult }
import baile.domain.experiment.{ Experiment, ExperimentStatus }
import baile.domain.images.augmentation._
import baile.domain.pipeline._
import baile.domain.pipeline.pipeline.GenericExperimentPipeline
import baile.domain.pipeline.result._
import baile.domain.table.{ ColumnDataType, ColumnVariableType }
import baile.domain.tabular.model.summary._
import baile.domain.tabular.model.{ ModelColumn, TabularModelClass }
import baile.domain.tabular.pipeline.TabularTrainPipeline
import baile.domain.tabular.result.TabularTrainResult
import org.mongodb.scala.MongoDatabase

import scala.util.Success

class ExperimentDaoSpec extends BaseSpec {

  "ExperimentDao" should {
    val mockedMongoDatabase: MongoDatabase = mock[MongoDatabase]
    val tabularModelPipelineSerializer: TabularModelPipelineSerializer = new TabularModelPipelineSerializer
    val cvModelPipelineSerializer: CVModelPipelineSerializer = new CVModelPipelineSerializer
    val genericExperimentPipelineSerializer: GenericExperimentPipelineSerializer = new GenericExperimentPipelineSerializer
    val serializerDelegator = new SerializerDelegator(tabularModelPipelineSerializer, cvModelPipelineSerializer, genericExperimentPipelineSerializer)
    val dao: ExperimentDao = new ExperimentDao(mockedMongoDatabase, serializerDelegator)

    val feParams: FeatureExtractorParams = FeatureExtractorParams.CreateNewFeatureExtractorParams(
      "feArch",
      Map(
        "param1" -> StringParam("foo"),
        "param1" -> BooleanParam(true),
        "param1" -> IntParam(42),
        "param1" -> FloatParam(0.3f)
      )
    )

    val trainParams = CommonTrainParams(
      inputSize = Some(InputSize(20, 40)),
      loi = Some(Seq(
        LabelOfInterest("label1", 0.2),
        LabelOfInterest("label2", 0.3)
      )),
      defaultVisualThreshold = Some(0.42f),
      iouThreshold = Some(0.24f),
      featureExtractorLearningRate = Some(0.33d),
      modelLearningRate = Some(0.23d)
    )

    val pipelineStepOne = CVTLTrainStep1Params(
      feParams = feParams,
      modelType = CVModelType.TLConsumer.Classifier("classifier"),
      modelParams = Map(
        "stringParams" -> StringParam("test"),
        "intParams" -> IntParam(1),
        "floatParams" -> FloatParam(1.2f),
        "booleanParams" -> BooleanParam(true),
        "seq_extraS" -> StringParams(Seq("value")),
        "seq_extra_F" -> FloatParams(Seq(0.1321F)),
        "seq_extra_b"-> BooleanParams(Seq(true)),
        "seq_extraI"-> IntParams(Seq(1)),
        "seq_empty" -> EmptySeqParam
      ),
      inputAlbumId = randomString(),
      testInputAlbumId = None,
      automatedAugmentationParams = Some(AutomatedAugmentationParams(
        augmentations = List(
          RotationParams(Seq(1.0f, 2.0f), true, 1),
          ShearingParams(Seq(10, 30), true, 1),
          NoisingParams(Seq(1, 2), 1),
          ZoomInParams(Seq(2), true, 1),
          ZoomOutParams(Seq(0.2f), true, 1),
          OcclusionParams(Seq(0.1f), OcclusionMode.Zero, true, 32, 1),
          TranslationParams(Seq(0.1f), TranslationMode.Constant, true, 1),
          SaltPepperParams(Seq(0.1f), 0.5f, 1),
          PhotometricDistortParams(PhotometricDistortAlphaBounds(0.5f, 1.5f), 18, 1),
          CroppingParams(Seq(0.25f), 1, false, 1),
          BlurringParams(Seq(0.5f), 1)
        ),
        bloatFactor = 42,
        generateSampleAlbum = false
      )),
      trainParams = Some(trainParams)
    )

    val pipelineStepTwo = CVTLTrainStep2Params(
      tuneFeatureExtractor = false,
      modelType = CVModelType.TLConsumer.Classifier("classifer"),
      modelParams = Map(
        "stringParams" -> StringParam("test"),
        "intParams" -> IntParam(1),
        "floatParams" -> FloatParam(1.2f),
        "booleanParams" -> BooleanParam(true)
      ),
      inputAlbumId = randomString(),
      testInputAlbumId = None,
      automatedAugmentationParams = None,
      trainParams = Some(trainParams)
    )

    val pipeline = CVTLTrainPipeline(
      pipelineStepOne,
      Some(pipelineStepTwo)
    )

    val tabularTrainPipeline = TabularTrainPipeline(
      samplingWeightColumnName = randomOf(None, Some(randomString())),
      predictorColumns = List.fill(randomInt(10))(randomColumn),
      responseColumn = ModelColumn(
        name = randomString(),
        displayName = randomString(),
        dataType = randomOf(
          ColumnDataType.String,
          ColumnDataType.Integer,
          ColumnDataType.Double,
          ColumnDataType.Long,
          ColumnDataType.Timestamp
        ),
        variableType = randomOf(ColumnVariableType.Continuous, ColumnVariableType.Categorical)
      ),
      inputTableId = randomString(),
      holdOutInputTableId = randomOf(None, Some(randomString())),
      outOfTimeInputTableId = randomOf(None, Some(randomString()))
    )

    val genericPipelineStep = PipelineStep(
      id = randomString(),
      operatorId = randomString(),
      inputs = Map(
        "inputValue" -> PipelineOutputReference(
          stepId = randomString(),
          outputIndex = randomInt(9999)
        )
      ),
      params = Map(
        "stringParam" -> PipelineParams.StringParam(randomString()),
        "intParam" -> PipelineParams.IntParam(randomInt(9999)),
        "floatParam" -> PipelineParams.FloatParam(1.2f),
        "booleanParam" -> PipelineParams.BooleanParam(randomBoolean()),
        "booleanSeqParam" -> PipelineParams.BooleanParams(Seq(randomBoolean(), randomBoolean())),
        "floatSeqParam" -> PipelineParams.FloatParams(Seq(1.2f, 3.4f)),
        "intSeqParam" -> PipelineParams.IntParams(Seq(randomInt(9999), randomInt(9999))),
        "stringSeqParam" -> PipelineParams.StringParams(Seq(randomString(), randomString()))
      ),
      coordinates = Some(PipelineCoordinates(x = 22, y = 24))
    )

    val genericExperimentPipeline = GenericExperimentPipeline(
      steps = Seq(genericPipelineStep),
      assets = Seq.empty
    )

    val trainStep = CVTLTrainStepResult(
      modelId = randomString(),
      outputAlbumId = Some(randomString()),
      testOutputAlbumId = None,
      autoAugmentationSampleAlbumId = None,
      summary = Some(CVModelSummary(
        Seq.empty[String],
        None,
        None,
        None
      )),
      testSummary = None,
      augmentationSummary = None,
      trainTimeSpentSummary = None,
      evaluateTimeSpentSummary = None,
      probabilityPredictionTableId = None,
      testProbabilityPredictionTableId = None
    )

    val trainResult = CVTLTrainResult(
      trainStep,
      Some(trainStep)
    )

    val tabularTrainResult = TabularTrainResult(
      modelId = randomString(),
      outputTableId = randomString(),
      holdOutOutputTableId = randomOf(None, Some(randomString())),
      outOfTimeOutputTableId = randomOf(None, Some(randomString())),
      predictedColumnName = randomString(),
      classes = Some(Seq(TabularModelClass(
        className = randomString(),
        probabilityColumnName = randomString()
      ))),
      summary = Some(randomOf(
        randomRegressionSummary(),
        randomClassificationSummary(),
        randomBinaryClassificationTrainSummary()
      )),
      holdOutSummary = randomOf(
        None,
        Some(randomRegressionSummary()),
        Some(randomClassificationSummary()),
        Some(randomBinaryClassificationEvaluationSummary())
      ),
      outOfTimeSummary = randomOf(
        None,
        Some(randomRegressionSummary()),
        Some(randomClassificationSummary()),
        Some(randomBinaryClassificationEvaluationSummary())
      ),
      predictorsSummary = Seq.fill(1)(randomOf(
        ParametricModelPredictorSummary(
          name = randomString(),
          coefficient = randomInt(999),
          stdErr = randomInt(999),
          pValue = randomInt(999),
          tValue = randomInt(999)
        )
      ))
    )

    val assetReference = AssetReference(
      id = randomString(),
      `type` = randomOf(
        AssetType.Album,
        AssetType.CvModel,
        AssetType.CvPrediction,
        AssetType.DCProject,
        AssetType.Experiment,
        AssetType.Flow,
        AssetType.OnlineJob,
        AssetType.Table,
        AssetType.TabularModel,
        AssetType.TabularPrediction
      )
    )

    val pipelineOperatorApplicationSummary = randomOf(
      SimpleSummary(values = Map(
        "intParam" -> PipelineResultValue.IntValue(randomInt(9999)),
        "stringParam" -> PipelineResultValue.StringValue(randomString()),
        "floatParam" -> PipelineResultValue.FloatValue(1.2f),
        "booleanParam" -> PipelineResultValue.BooleanValue(randomBoolean())
      )),
      ConfusionMatrix(confusionMatrixCells = Seq(
        ConfusionMatrixCell(
          actualLabel = Some(randomInt(9999)),
          predictedLabel = Some(randomInt(9999)),
          count = randomInt(9999)
        )),
        labels = Seq("label1", "label2")
      )
    )

    val genericExperimentStepResult = GenericExperimentStepResult(
      id = randomString(),
      assets = Seq(assetReference),
      summaries = Seq(pipelineOperatorApplicationSummary),
      outputValues = Map(
        randomInt(9999) -> PipelineResultValue.IntValue(randomInt(9999)),
        randomInt(9999) -> PipelineResultValue.StringValue(randomString()),
        randomInt(9999) -> PipelineResultValue.FloatValue(1.2f),
        randomInt(9999) -> PipelineResultValue.BooleanValue(randomBoolean())
      ),
      executionTime = randomInt(9999999),
      failureMessage = None
    )

    val genericExperimentResult = GenericExperimentResult(
      steps = Seq(genericExperimentStepResult)
    )

    val experiment = Experiment(
      name = "test experiment",
      ownerId = UUID.randomUUID(),
      created = Instant.now(),
      updated = Instant.now(),
      description = None,
      status = ExperimentStatus.Running,
      pipeline = pipeline,
      result = Some(trainResult)
    )

    val experimentForTabularPipeline = Experiment(
      name = "Tabular experiment",
      ownerId = UUID.randomUUID(),
      created = Instant.now(),
      updated = Instant.now(),
      description = None,
      status = ExperimentStatus.Running,
      pipeline = tabularTrainPipeline,
      result = Some(tabularTrainResult)
    )

    val experimentForGenericExperimentPipeline = Experiment(
      name = "Generic experiment",
      ownerId = UUID.randomUUID(),
      created = Instant.now(),
      updated = Instant.now(),
      description = None,
      status = ExperimentStatus.Running,
      pipeline = genericExperimentPipeline,
      result = Some(genericExperimentResult)
    )

    "convert experiment to document and back (cv train pipeline)" in {
      val document = dao.entityToDocument(experiment)
      val restoredModel = dao.documentToEntity(document)
      restoredModel shouldBe Success(experiment)
    }

    "convert experiment to document and back (tabular train pipeline)" in {
      val document = dao.entityToDocument(experimentForTabularPipeline)
      val restoredModel = dao.documentToEntity(document)
      restoredModel shouldBe Success(experimentForTabularPipeline)
    }

    "convert experiment to document and back when Experiment pipeline is of the type GenericExperimentPipeline" in {
      val document = dao.entityToDocument(experimentForGenericExperimentPipeline)
      val restoredModel = dao.documentToEntity(document)
      restoredModel shouldBe Success(experimentForGenericExperimentPipeline)
    }
  }

  private def randomColumn: ModelColumn = ModelColumn(
    name = randomString(),
    displayName = randomString(),
    dataType = randomOf(
      ColumnDataType.Boolean,
      ColumnDataType.Double,
      ColumnDataType.Integer,
      ColumnDataType.Long,
      ColumnDataType.String,
      ColumnDataType.Timestamp
    ),
    variableType = randomOf(ColumnVariableType.Categorical, ColumnVariableType.Categorical)
  )

  private def randomRegressionSummary(): RegressionSummary =
    RegressionSummary(
      rmse = randomInt(999),
      r2 = randomInt(999),
      mape = randomInt(999)
    )

  private def randomClassificationSummary(): ClassificationSummary =
    ClassificationSummary(
      confusionMatrix = Seq.fill(randomInt(10))(
        ClassConfusion(
          className = randomString(),
          truePositive = randomInt(999),
          trueNegative = randomInt(999),
          falsePositive = randomInt(999),
          falseNegative = randomInt(999),
        )
      )
    )

  private def randomBinaryClassificationEvaluationSummary(): BinaryClassificationEvaluationSummary =
    BinaryClassificationEvaluationSummary(
      classificationSummary = randomClassificationSummary(),
      ks = randomInt(9999)
    )

  private def randomBinaryClassificationTrainSummary(): BinaryClassificationTrainSummary =
    BinaryClassificationTrainSummary(
      evaluationSummary = randomBinaryClassificationEvaluationSummary(),
      areaUnderROC = randomInt(9999),
      rocValues = Seq.fill(randomInt(10))(RocValue(randomInt(9999), randomInt(9999))),
      f1Score = randomInt(9999),
      precision = randomInt(9999),
      recall = randomInt(9999),
      threshold = randomInt(9999)
    )

}
