package cortex.task.computer_vision

import cortex.JsonSupport.SnakeJson
import cortex.task.computer_vision.LocalizationParams._
import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import play.api.libs.json.JsSuccess

class LocalizationParamsSerializationTest extends FlatSpec {

  "json serializer" should "deserialize TrainTaskResult properly" in {
    val jsonString =
      """{
        |  "model_id": "12",
        |  "feature_extractor_id": "34",
        |  "predictions": [
        |    {
        |      "filename": "56.png",
        |      "tags": [
        |        [
        |          117,
        |          53,
        |          200,
        |          129,
        |          "Building",
        |          0.4
        |        ]
        |      ]
        |    }
        |  ],
        |  "m_a_p": 0.25,
        |  "confusion_matrix": {
        |    "confusion_matrix_cells": [
        |      {
        |        "actual_label_index": 0,
        |        "predicted_label_index": null,
        |        "value": 2
        |      }
        |    ],
        |    "labels": [
        |      "Truck"
        |    ]
        |  },
        |  "augmentation_result": null,
        |  "data_fetch_time": 1,
        |  "training_time": 426,
        |  "prediction_time": 32,
        |  "save_model_time": 3,
        |  "save_feature_extractor_time": 1,
        |  "prediction_table": {
        |     "filename_column": "filenameColumn",
        |     "probability_columns": [
        |         {"class_name": "probabilityClass", "column_name": "probabilityColumn"}
        |     ],
        |     "area_columns": {"x_min": "xmin", "y_min": "ymin", "x_max": "xmax", "y_max": "ymax"},
        |     "reference_id_column": "referenceIdColumn"
        |  }
        |}
      """.stripMargin
    //scalastyle:off magic.number
    val expectedTrainTaskResult = TrainTaskResult(
      modelId                  = "12",
      featureExtractorId       = "34",
      predictions              = Seq(
        PredictionResult(
          filename = "56.png",
          tags     = List(Tag(117, 53, 200, 129, "Building", Some(0.4)))
        )
      ),
      mAP                      = 0.25,
      confusionMatrix          = ConfusionMatrix(
        confusionMatrixCells = Seq(
          ConfusionMatrixCell(
            actualLabelIndex    = Some(0),
            predictedLabelIndex = None,
            value               = 2
          )
        ),
        labels               = Seq("Truck")
      ),
      augmentationResult       = None,
      dataFetchTime            = 1,
      trainingTime             = 426,
      predictionTime           = 32,
      saveModelTime            = 3,
      saveFeatureExtractorTime = 1,
      predictionTable          = Some(PredictionTable(
        filenameColumn     = "filenameColumn",
        probabilityColumns = Seq(ProbabilityClassColumn(
          className  = "probabilityClass",
          columnName = "probabilityColumn"
        )),
        areaColumns        = Some(AreaColumns("xmin", "ymin", "xmax", "ymax")),
        referenceIdColumn  = "referenceIdColumn"
      ))
    )

    val deserializedTrainTaskResult = SnakeJson.fromJson[TrainTaskResult](SnakeJson.parse(jsonString))

    deserializedTrainTaskResult shouldBe JsSuccess(expectedTrainTaskResult)
  }
}
