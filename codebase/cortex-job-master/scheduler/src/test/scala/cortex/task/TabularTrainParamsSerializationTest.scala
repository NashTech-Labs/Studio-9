package cortex.task

import cortex.JsonSupport.SnakeJson
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.tabular_data.{ AllowedModelPrimitive, AllowedTaskType }
import cortex.task.tabular_data.tabularpipeline.TabularPipelineParams.TabularTrainParams
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class TabularTrainParamsSerializationTest extends FlatSpec {
  "TabularTrainParams" should "be serialized to json string properly" in {

    val expectedString =
      """{
        |  "train_input_paths": [],
        |  "task_type": "classifier",
        |  "model_primitive": "linear",
        |  "response": "response",
        |  "numerical_predictors": [],
        |  "categorical_predictors": [],
        |  "hyperparams_dict": {
        |    "IntParam": 1,
        |    "DoubleParam": 2.5
        |  },
        |  "storage_access_params": {
        |    "bucket": "baseBucket",
        |    "access_key": "accessKey",
        |    "secret_key": "secretKey",
        |    "region": "",
        |    "endpoint_url": "fakeS3Endpoint",
        |    "storage_type": "S3"
        |  },
        |  "mvh_model_id": "mvh-123",
        |  "models_base_path": "tabular/models",
        |  "action": "train"
        |}
      """.stripMargin

    val s3AccessParams = S3AccessParams(
      bucket      = "baseBucket",
      accessKey   = "accessKey",
      secretKey   = "secretKey",
      region      = "",
      endpointUrl = Some("fakeS3Endpoint")
    )
    val tabularTrainParams = TabularTrainParams(
      trainInputPaths       = Seq(),
      taskType              = AllowedTaskType.Classifier,
      modelPrimitive        = AllowedModelPrimitive.Linear,
      weightsCol            = None,
      response              = "response",
      numericalPredictors   = Seq(),
      categoricalPredictors = Seq(),
      hyperparamsDict       = Map("IntParam" -> IntHyperParam(1), "DoubleParam" -> DoubleHyperParam(2.5)),
      storageAccessParams   = s3AccessParams,
      mvhModelId            = "mvh-123",
      modelsBasePath        = "tabular/models"
    )

    SnakeJson.toJson(tabularTrainParams) shouldBe SnakeJson.parse(expectedString)
  }
}
