package cortex.task

import cortex.JsonSupport.SnakeJson
import cortex.task.StorageAccessParams.S3AccessParams
import cortex.task.common.ClassReference
import cortex.task.tabular_data.tabularpipeline.TabularPipelineParams.TabularPredictParams
import org.scalatest.FlatSpec
import org.scalatest.Matchers._

class TabularPredictParamsSerializationTest extends FlatSpec {

  "TabularPredictParams" should "be serialized to json string properly" in {

    val expectedString =
      """{
        |  "validate_input_paths": [],
        |  "model_id": "tabular-model-123",
        |  "columns_mapping": {},
        |  "predict_col_name": "name",
        |  "probability_col_names": [
        |    [
        |      "col1",
        |      "prob1"
        |    ],
        |    [
        |      "col2",
        |      "prob2"
        |    ]
        |  ],
        |  "storage_access_params": {
        |    "bucket": "baseBucket",
        |    "access_key": "accessKey",
        |    "secret_key": "secretKey",
        |    "region": "",
        |    "endpoint_url": "fakeS3Endpoint",
        |    "storage_type": "S3"
        |  },
        |  "class_reference": {
        |    "module_name": "module_name",
        |    "class_name": "class_name"
        |  },
        |  "predict_path": "tabular/prediction",
        |  "models_base_path": "tabular/models",
        |  "action": "predict"
        |}
      """.stripMargin

    val s3AccessParams = S3AccessParams(
      bucket      = "baseBucket",
      accessKey   = "accessKey",
      secretKey   = "secretKey",
      region      = "",
      endpointUrl = Some("fakeS3Endpoint")
    )
    val tabularPredictParams = TabularPredictParams(
      validateInputPaths  = Seq(),
      modelId             = "tabular-model-123",
      columnsMapping      = Map(),
      predictColName      = "name",
      probabilityColNames = List("col1" -> "prob1", "col2" -> "prob2"),
      storageAccessParams = s3AccessParams,
      predictPath         = "tabular/prediction",
      classReference      = ClassReference(None, "module_name", "class_name"),
      modelsBasePath      = "tabular/models"
    )

    SnakeJson.toJson(tabularPredictParams) shouldBe SnakeJson.parse(expectedString)
  }
}
