package cortex.api.job.computervision

import java.util.UUID

import cortex.api.job.common.{ ClassReference, ConfusionMatrix, ConfusionMatrixCell, ModelReference }
import cortex.api.job.album.common.{ Image, Tag, TagArea, TaggedImage }
import cortex.api.job.computervision.DeleteResult.Code
import cortex.api.job.table.{ProbabilityClassColumn, TableMeta}

// scalastyle:off
object Sample {

  val trainInputImages = Seq(
    TaggedImage(
      image = Some(Image("https://deepcortex.ai/pic1.png", Some("1"))),
      tags = Seq(
        Tag(
          label = "car",
          area = Some(TagArea(
            top = 20,
            left = 170,
            height = 400,
            width = 1600
          ))
        ),
        Tag(
          label = "man",
          area = Some(TagArea(
            top = 70,
            left = 600,
            height = 1200,
            width = 400
          ))
        )
      )
    ),
    TaggedImage(
      image = Some(Image("https://deepcortex.ai/pic2.png", Some("2"))),
      tags = Seq(
        Tag(
          label = "man",
          area = Some(TagArea(
            top = 900,
            left = 1500,
            height = 300,
            width = 500
          ))
        ),
        Tag(
          label = "car",
          area = Some(TagArea(
            top = 30,
            left = 15,
            height = 800,
            width = 500
          ))
        )
      )
    )
  )

  val filePathPrefix = s"baile/data/albums${UUID.randomUUID}"

  val tlModelType = TLModelType(TLModelType.Type.ClassifierType(ClassReference(
    packageLocation = Some("/ml_lib/classifiers/vgg16_1"),
    moduleName = "classifier",
    className = "vgg16_1"
  )))
  val cvModelType = CVModelType(CVModelType.Type.TlModel(
    TLModel(
      modelType = Some(tlModelType),
      featureExtractorClassReference = None
    )
  ))

  val featureExtractorClassReference = ClassReference(
    packageLocation = Some("/ml_lib/feature_extractor/scae_1"),
    moduleName = "feature_extractor",
    className = "scae_1"
  )

  object Train {

    val trainRequest = CVModelTrainRequest(
      featureExtractorId = Some("some_feature_extractor_id"),
      featureExtractorClassReference = Some(featureExtractorClassReference),
      images = trainInputImages,
      filePathPrefix = filePathPrefix,
      modelType = Some(tlModelType),
      augmentationParams = None,
      featureExtractorParameters = Map(
        "foo" -> ParameterValue(ParameterValue.Value.IntValue(0)),
        "bar" -> ParameterValue(ParameterValue.Value.StringValue("baz")),
        "seq_bar" -> ParameterValue(ParameterValue.Value.StringValues(StringSequenceValue(Seq("baz1", "baz2"))))
      ),
      modelParameters = Map(
        "foo" -> ParameterValue(ParameterValue.Value.IntValue(0)),
        "bar" -> ParameterValue(ParameterValue.Value.StringValue("baz")),
        "seq_foo" -> ParameterValue(ParameterValue.Value.IntValues(IntSequenceValue(Seq(0, 1))))
      ),
      probabilityPredictionTable = Some(TableMeta("table", "schema"))
    )

    val modelId = UUID.randomUUID.toString

    val initialPredictOutputImages = Seq(
      PredictedImage(
        image = trainInputImages.head.image,
        predictedTags = Seq(
          PredictedTag(
            tag = Some(Tag(
              label = "car",
              area = Some(TagArea(
                top = 20,
                left = 170,
                height = 400,
                width = 1600
              ))
            )),
            confidence = 1
          ),
          PredictedTag(
            tag = Some(Tag(
              label = "car",
              area = Some(TagArea(
                top = 70,
                left = 600,
                height = 1200,
                width = 400
              ))
            )),
            confidence = .5
          )
        )
      ),
      PredictedImage(
        image = trainInputImages(1).image,
        predictedTags = Seq(
          PredictedTag(
            tag = Some(Tag(
              label = "man",
              area = Some(TagArea(
                top = 900,
                left = 1500,
                height = 300,
                width = 500
              ))
            )),
            confidence = 0.2
          ),
          PredictedTag(
            tag = Some(Tag(
              label = "man",
              area = Some(TagArea(
                top = 30,
                left = 15,
                height = 800,
                width = 500
              ))
            )),
            confidence = 0.3
          )
        )
      )
    )

    val initialPredictConfusionCells = Seq(
      ConfusionMatrixCell(Some(0), Some(0), 2),
      ConfusionMatrixCell(Some(0), Some(1), 1),
      ConfusionMatrixCell(Some(1), Some(1), 1),
      ConfusionMatrixCell(Some(1), Some(0), 2)
    )

    val labels = Seq("label1", "label2")

    val confusionMatrix = ConfusionMatrix(initialPredictConfusionCells, labels)

    val featureExtractorReference = ModelReference(
      "some_feature_extractor_id",
      s"cortex-job-master/fe/fe_58jgs8w"
    )

    val cvModelReference = ModelReference(
      modelId,
      s"cortex-job-master/models/$modelId/cv_58jgs8w"
    )

    val trainResult = CVModelTrainResult(
      featureExtractorReference = Some(featureExtractorReference),
      cvModelReference = Some(cvModelReference),
      images = initialPredictOutputImages,
      confusionMatrix = Some(confusionMatrix),
      map = Some(0.23),
      dataFetchTime = 1000L,
      trainingTime = 1000L,
      saveModelTime = 1000L,
      predictionTime = 1000L,
      reconstructionLoss = Some(0.10),
      pipelineTimings = Map(
        "step1" -> 1000L,
        "step2" -> 1000L,
        "step3" -> 1000L,
        "step4" -> 1000L
      ),
      probabilityPredictionTableSchema = Some(ProbabilityPredictionTableSchema(
        probabilityColumns = Seq(
          ProbabilityClassColumn("label1", "column1"),
          ProbabilityClassColumn("label2", "column2")
        ),
        imageFileNameColumnName = "filename"
      ))
    )

    val trainResultWithAugmentedSummary = CVModelTrainResult(
      featureExtractorReference = Some(featureExtractorReference),
      cvModelReference = Some(cvModelReference),
      images = initialPredictOutputImages,
      confusionMatrix = Some(confusionMatrix),
      map = Some(0.23),
      augmentationSummary = Some(cortex.api.job.album.augmentation.Sample.augmentationSummary),
      dataFetchTime = 1000L,
      trainingTime = 1000L,
      saveModelTime = 1000L,
      predictionTime = 1000L
    )

  }

  object Predict {

    val predictInputImages = Seq(
      Image("https://deepcortex.ai/pic3.png", Some("3")),
      Image("https://deepcortex.ai/pic4.png", Some("4")),
      Image("https://deepcortex.ai/pic5.png", Some("5")),
      Image("https://deepcortex.ai/pic6.png", Some("6"))
    )

    val videoParams = VideoParams(
      targetVideoFilePath = "prediction.mp4",
      videoAssembleFrameRate = 1,
      videoAssembleHeight = 640,
      videoAssembleWidth = 1280
    )

    val predictRequest = PredictRequest(
      modelType = Some(cvModelType),
      modelId = Train.modelId,
      images = predictInputImages,
      filePathPrefix = filePathPrefix,
      videoParams = Some(videoParams),
      labelsOfInterest = Seq(
        LabelOfInterest("car", 0.5),
        LabelOfInterest("man", 0.5)
      )
    )

    val predictResult = PredictResult(
      images = predictInputImages.map(image =>
        PredictedImage(
          image = Some(image.copy(fileSize = Some(389483L))),
          predictedTags = Seq(
            PredictedTag(
              tag = Some(Tag(
                label = "man",
                area = Some(TagArea(
                  top = 30,
                  left = 15,
                  height = 800,
                  width = 500
                ))
              )),
              confidence = 1
            ),
            PredictedTag(
              tag = Some(Tag(
                label = "car",
                area = Some(TagArea(
                  top = 70,
                  left = 600,
                  height = 1200,
                  width = 400
                ))
              )),
              confidence = 1
            )
          )
        )
      ),
      videoFileSize = Some(34698234),
      dataFetchTime = 1000L,
      loadModelTime = 1000L,
      predictionTime = 1000L,
      pipelineTimings = Map(
        "step1" -> 1000L,
        "step2" -> 1000L,
        "step3" -> 1000L,
        "step4" -> 1000L
      )
    )

  }

  object Evaluate {

    val evaluateRequest = EvaluateRequest(
      modelType = Some(cvModelType),
      images = trainInputImages,
      modelId = Train.modelId,
      filePathPrefix = filePathPrefix,
      labelsOfInterest = Seq(
        LabelOfInterest("car", 0.5),
        LabelOfInterest("man", 0.5)
      )
    )

    val evaluateOutputImages = Train.initialPredictOutputImages

    val evaluateConfusionMatrixCells = Seq(
      ConfusionMatrixCell(Some(0), Some(0), 2),
      ConfusionMatrixCell(Some(0), Some(1), 1),
      ConfusionMatrixCell(Some(1), Some(1), 3),
      ConfusionMatrixCell(Some(1), Some(0), 2)
    )

    val labels = Seq("label2", "label3")

    val confusionMatrix = ConfusionMatrix(evaluateConfusionMatrixCells, labels)

    val evaluateResult = EvaluateResult(
      images = evaluateOutputImages,
      map = Some(0.59),
      confusionMatrix = Some(confusionMatrix),
      dataFetchTime = 1000L,
      loadModelTime = 1000L,
      scoreTime = 1000L,
      pipelineTimings = Map(
        "step1" -> 1000L,
        "step2" -> 1000L,
        "step3" -> 1000L,
        "step4" -> 1000L
      )
    )

  }


  object Delete {

    val modelId = UUID.randomUUID.toString

    val deleteRequest = DeleteRequest(modelId)

    val deleteResult = DeleteResult(Code.DELETED)

  }

  object Import {

    val request = CVModelImportRequest(
      path = "/some_s3_path/model_file",
      modelType = Some(cvModelType),
      feOnly = false
    )

    val featureExtractorReference = ModelReference(
      id = "generated_unique_id",
      filePath = "/job_master_s3_models_path/fe_file_uuid"
    )

    val cvModelReference = ModelReference(
      id = "generated_unique_id",
      filePath = "/job_master_s3_models_path/model_file_uuid"
    )

    val response = CVModelImportResult(
      featureExtractorReference = Some(featureExtractorReference),
      cvModelReference = Some(cvModelReference)
    )

  }
}
