package cortex.api.job.tabular

import java.util.UUID

import cortex.api.job.table._
import cortex.api.job.common.{ ClassReference, ModelReference }

// scalastyle:off
object Sample {

  // !Values are not accurate! Especially summary statistics.
  // Remember, this is just dummy sample to show how to fill messages and what results are expected on what inputs.

  object Train {

    // Input Table:
    // "main.abalone_train"
    // | predictor1: Int | predictor2: String | response: String | weight: Double
    // | 33              | "John"             | "male"           | 0.92
    // | 12              | "Lisa"             | "female"         | 0.23
    // | 31              | "Kate"             | "female"         | 0.59
    // | 18              | "Bob"              | "male"           | 0.12

    val predictors = Seq(
      TableColumn("predictor1", DataType.INTEGER, VariableType.CONTINUOUS),
      TableColumn("predictor2", DataType.STRING, VariableType.CONTINUOUS)
    )
    val response = TableColumn("response", DataType.STRING, VariableType.CATEGORICAL)
    val weight = TableColumn("weight", DataType.DOUBLE, VariableType.CONTINUOUS)

    val tableColumns = predictors ++ Seq(response, weight)
    val inputTable = Table(Some(TableMeta(schema = "main", name = "abalone_train")), columns = tableColumns)
    val inputSource = DataSource(table = Some(inputTable))

    val outputTable = Table(Some(TableMeta(schema = "main", name = "abalone_initial_prediction")))
    val outputSource = DataSource(table = Some(outputTable))

    val trainRequest = TrainRequest(
      input = Some(inputSource),
      predictors = predictors,
      response = Some(response),
      weight = Some(weight),
      output = Some(outputSource),
      dropPreviousResultTable = false,
      predictionResultColumnName = response.name + "_predicted",
      probabilityColumnsPrefix = Some("class_")
    )

    val predictorsSummary = Seq(
      PredictorSummary(
        name = "predictor1",
        summary = PredictorSummary.Summary.TreeModelPredictorSummary(TreeModelPredictorSummary(0.3))
      ),
      PredictorSummary(
        name = "predictor2_Lisa",
        summary = PredictorSummary.Summary.TreeModelPredictorSummary(TreeModelPredictorSummary(0.5))
      ),
      PredictorSummary(
        name = "predictor2_Kate",
        summary = PredictorSummary.Summary.TreeModelPredictorSummary(TreeModelPredictorSummary(0.1))
      ),
      PredictorSummary(
        name = "predictor2_Bob",
        summary = PredictorSummary.Summary.TreeModelPredictorSummary(TreeModelPredictorSummary(0.7))
      )
    )

    val classificationSummary = ClassificationSummary(Seq(
      ClassConfusion(
        className = "male",
        truePositive = 1,
        trueNegative = 0,
        falsePositive = 2,
        falseNegative = 0
      ),
      ClassConfusion(
        className = "female",
        truePositive = 0,
        trueNegative = 0,
        falsePositive = 1,
        falseNegative = 0
      )
    ))

    val binaryClassificationTrainSummary = BinaryClassificationTrainSummary(
      areaUnderRoc = 0.68,
      rocValues = Seq(
        RocValue(0.1, 0.3),
        RocValue(0.9, 0.1)
      ),
      f1Score = 0.8,
      precision = 0.3,
      recall = 0.1,
      threshold = 0.9,
      binaryClassificationEvalSummary = Some(BinaryClassificationEvalSummary(
        generalClassificationSummary = Some(classificationSummary),
        ks = 0.1
      ))
    )

    // Output Table:
    // "main.abalone_initial_prediction"
    // | predictor1: Int | predictor2: String | response: String | weight: Double  | response_predicted: String | class_male: Double | class_female: Double |
    // | 33              | "John"             | "male"           | 0.92            | "male"                     | 0.7                | 0.3                  |
    // | 12              | "Lisa"             | "female"         | 0.23            | "male"                     | 0.68               | 0.32                 |
    // | 31              | "Kate"             | "female"         | 0.59            | "male"                     | 0.92               | 0.08                 |
    // | 18              | "Bob"              | "male"           | 0.12            | "female"                   | 0.33               | 0.77                 |

    val modelId = UUID.randomUUID.toString

    val trainResult = TrainResult(
      modelId = modelId,
      modelType = ModelType.BINARY,
      formula = "fo <- y ~ x1*x2",
      summary = TrainResult.Summary.BinaryClassificationTrainSummary(binaryClassificationTrainSummary),
      predictorsSummary = predictorsSummary,
      probabilityColumns = Seq(
        ProbabilityClassColumn(
          className = "male",
          columnName = "class_male"
        ),
        ProbabilityClassColumn(
          className = "female",
          columnName = "class_female"
        )
      ),
      output = Some(outputSource),
      modelPrimitive = "randomForest",
      modelFilePath = s"cortex-job-master/models/$modelId/cv_58jgs8w"
    )

  }

  object Predict {

    // Input Table:
    // "main.abalone_holdout"
    // | age: Int        | name: String |
    // | 55              | "Alex"       |
    // | 2               | "Daniel"     |
    // | 15              | "Pavel"      |
    // | 16              | "Ashley"     |

    val tableColumns = Seq(
      TableColumn("predictor1", DataType.INTEGER, VariableType.CONTINUOUS),
      TableColumn("predictor2", DataType.STRING, VariableType.CATEGORICAL)
    )

    val inputTable = Table(Some(TableMeta(schema = "main", name = "abalone_holdout")), columns = tableColumns)
    val inputSource = DataSource(table = Some(inputTable))

    val outputTable = Table(Some(TableMeta(schema = "main", name = "abalone_holdout_prediction")))
    val outputSource = DataSource(table = Some(outputTable))

    val predictors = Seq(
      ColumnMapping("predictor1", "age"),
      ColumnMapping("predictor2", "name")
    )

    val modelReference = ClassReference(
      packageLocation = Some("/ml_lib/tabular/"),
      moduleName = "fused_pipeline_stage",
      className = "FusedTabularPipeline"
    )

    val predictRequest = PredictRequest(
      modelId = Train.modelId,
      input = Some(inputSource),
      output = Some(outputSource),
      predictors = predictors,
      dropPreviousResultTable = false,
      predictionResultColumnName = "male_predicted",
      probabilityColumns = Seq(
        ProbabilityClassColumn(
          className = "male",
          columnName = "class_male"
        ),
        ProbabilityClassColumn(
          className = "female",
          columnName = "class_female"
        )
      ),
      modelReference = Some(modelReference)
    )

    // Output Table:
    // "main.abalone_holdout_prediction"
    // | age: Int        | name: String       | $male_predicted: String    | class_male: Double | class_female: Double |
    // | 55              | "Alex"             | "male"                     | 0.56               | 0.44                 |
    // | 2               | "Daniel"           | "female"                   | 0.11               | 0.89                 |
    // | 15              | "Pavel"            | "female"                   | 0.49               | 0.51                 |
    // | 16              | "Ashley"           | "male"                     | 0.66               | 0.34                 |

    val predictionResult = PredictionResult(output = Some(outputSource))

  }

  object Evaluate {

    // Input Table:
    // "main.abalone_evaluate"
    // | age: Int        | name: String       | sex: String      | weight: Double
    // | 5               | "Veronica"         | "female"         | 0.11
    // | 89              | "George"           | "male"           | 0.55
    // | 88              | "Jennifer"         | "female"         | 0.78
    // | 23              | "Stella"           | "female"         | 0.99

    val tableColumns = Seq(
      TableColumn("age", DataType.INTEGER, VariableType.CONTINUOUS),
      TableColumn("name", DataType.STRING, VariableType.CATEGORICAL),
      TableColumn("sex", DataType.STRING, VariableType.CATEGORICAL),
      TableColumn("weight", DataType.DOUBLE, VariableType.CONTINUOUS)
    )
    val inputTable = Table(Some(TableMeta(schema = "main", name = "abalone_evaluate")), columns = tableColumns)
    val inputSource = DataSource(table = Some(inputTable))

    val predictors = Seq(
      ColumnMapping("predictor1", "age"),
      ColumnMapping("predictor2", "name")
    )
    val weight = ColumnMapping("weight", "weight")
    val response = ColumnMapping("response", "sex")

    val outputTable = Table(Some(TableMeta(schema = "main", name = "abalone_evaluate_result")))
    val outputSource = DataSource(table = Some(outputTable))

    val modelReference = ClassReference(
      packageLocation = Some("/ml_lib/tabular/"),
      moduleName = "fused_pipeline_stage",
      className = "FusedTabularPipeline"
    )

    val evaluateRequest = EvaluateRequest(
      modelId = Train.modelId,
      input = Some(inputSource),
      output = Some(outputSource),
      predictors = predictors,
      response = Some(response),
      weight = Some(weight),
      dropPreviousResultTable = false,
      predictionResultColumnName = response.currentName + "_predicted",
      probabilityColumns = Seq(
        ProbabilityClassColumn(
          className = "male",
          columnName = "class_male"
        ),
        ProbabilityClassColumn(
          className = "female",
          columnName = "class_female"
        )
      ),
      modelReference = Some(modelReference)
    )

    val classificationSummary = ClassificationSummary(Seq(
      ClassConfusion(
        className = "male",
        truePositive = 0,
        trueNegative = 0,
        falsePositive = 1,
        falseNegative = 0
      ),
      ClassConfusion(
        className = "female",
        truePositive = 2,
        trueNegative = 0,
        falsePositive = 1,
        falseNegative = 1
      )
    ))

    // Values are close to being random.
    val binaryClassificationEvalSummary = BinaryClassificationEvalSummary(
      generalClassificationSummary = Some(classificationSummary),
      ks = 0.81
    )

    // Output Table:
    // "main.abalone_evaluate_result"
    // | age: Int        | name: String       | sex: String      | weight: Double  | sex_predicted: String      | class_male: Double | class_female: Double |
    // | 5               | "Veronica"         | "female"         | 0.11            | "male"                     | 0.58               | 0.42                 |
    // | 89              | "George"           | "male"           | 0.55            | "female"                   | 0.03               | 0.97                 |
    // | 88              | "Jennifer"         | "female"         | 0.78            | "female"                   | 0.12               | 0.88                 |
    // | 23              | "Stella"           | "female"         | 0.99            | "female"                   | 0.44               | 0.56                 |

    val evaluationResult = EvaluationResult(
      summary = EvaluationResult.Summary.BinaryClassificationEvalSummary(binaryClassificationEvalSummary),
      output = Some(outputSource)
    )

  }

  object Import {

    val modelReference: ClassReference = ClassReference(
      packageLocation = Some("location"),
      className = "class",
      moduleName = "moduleName"
    )

    val tabularModelImportRequest: TabularModelImportRequest =
      TabularModelImportRequest("path/to/file", Some(modelReference))

    val tabularModelReference: ModelReference = ModelReference("id", "file/path")

    val tabularModelImportResult: TabularModelImportResult = TabularModelImportResult(
      tabularModelReference = Some(tabularModelReference)
    )

  }

}
