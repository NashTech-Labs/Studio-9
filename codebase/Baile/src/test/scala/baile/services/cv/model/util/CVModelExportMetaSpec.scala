package baile.services.cv.model.util

import baile.ExtendedBaseSpec
import baile.services.cv.model.util.export.CVModelExportMeta
import baile.services.cv.model.util.export.CVModelExportMeta.{ AlbumLabelMode, CVModelType, ClassReference, Version }
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json.Json

class CVModelExportMetaSpec extends ExtendedBaseSpec with TableDrivenPropertyChecks {

  case class Test(version: String, json: String, expectedMeta: CVModelExportMeta)

  List(
    Test(
      "initial",
      """{
        "name": "model",
        "modelType": {
          "labelMode": "LOCALIZATION",
          "name": "RFBNET"
        },
        "featureExtractorArchitecture": "VGG16_RFB",
        "localizationMode": "TAGS",
        "summary": {
          "labels": ["label1", "label2", "label3"],
          "confusionMatrix":[
            { "actualLabel": 1, "predictedLabel": 0, "count": 3 },
            { "actualLabel": 2, "predictedLabel": 0, "count": 3 },
            { "actualLabel": 1, "predictedLabel": 1, "count": 3 },
            { "actualLabel": 1, "predictedLabel": 2, "count": 3 }
          ],
          "mAP": 42
        },
        "description": "test model"
      }""",
      CVModelExportMeta(
        name = Some("model"),
        modelType = CVModelType.TL(
          consumer = CVModelType.TLConsumer.Localizer(
            classReference = ClassReference(
              moduleName = "ml_lib.detectors.rfb_detector.RFBDetector",
              className = "RFBDetector",
              packageName = "deepcortex-ml-lib",
              packageVersion = None
            )
          ),
          featureExtractorReference = ClassReference(
            moduleName = "ml_lib.feature_extractors.backbones.vgg16_rfb",
            className = "VGG16_RFB",
            packageName = "deepcortex-ml-lib",
            packageVersion = None
          )
        ),
        description = Some("test model"),
        classNames = Some(Seq("label1", "label2", "label3"))
      )
    ),
    Test(
      "initial (FE)",
      """{
        "name": "fe",
        "architecture": "STACKED_AUTOENCODER",
        "summary": {
          "labels": ["label1", "label2", "label3"],
          "confusionMatrix":[
            { "actualLabel": 1, "predictedLabel": 0, "count": 3 },
            { "actualLabel": 2, "predictedLabel": 0, "count": 3 },
            { "actualLabel": 1, "predictedLabel": 1, "count": 3 },
            { "actualLabel": 1, "predictedLabel": 2, "count": 3 }
          ],
          "mAP": 42
        }
      }""",
      CVModelExportMeta(
        name = Some("fe"),
        modelType = CVModelType.TL(
          consumer = CVModelType.TLConsumer.Decoder(
            classReference = ClassReference(
              moduleName = "ml_lib.models.autoencoder.scae_model",
              className = "SCAEModel",
              packageName = "deepcortex-ml-lib",
              packageVersion = None
            )
          ),
          featureExtractorReference = ClassReference(
            moduleName = "ml_lib.feature_extractors.backbones.scae",
            className = "StackedAutoEncoder",
            packageName = "deepcortex-ml-lib",
            packageVersion = None
          ),
          featureExtractorOnly = true
        ),
        description = None,
        classNames = Some(Seq("label1", "label2", "label3"))
      )
    ),
    Test(
      "2",
      """{
        "__version": "2",
        "name": "model",
        "modelType": {
          "__type": "TL",
          "consumer": {
            "tlType": "CLASSIFIER",
            "classReference": {
              "moduleName": "ml_lib.classifiers.kpca_mnl.models.kpca_mnl",
              "className": "KPCA_MNL",
              "packageName": "deepcortex-ml-lib"
            }
          },
          "featureExtractorReference": {
            "moduleName": "ml_lib.feature_extractors.backbones",
            "className": "SqueezeNextReduced",
            "packageName": "deepcortex-ml-lib"
          }
        },
        "description": "test model",
        "classNames": ["something"]
      }""",
      CVModelExportMeta(
        name = Some("model"),
        modelType = CVModelType.TL(
          consumer = CVModelType.TLConsumer.Classifier(
            classReference = ClassReference(
              moduleName = "ml_lib.classifiers.kpca_mnl.models.kpca_mnl",
              className = "KPCA_MNL",
              packageName = "deepcortex-ml-lib",
              packageVersion = None
            )
          ),
          featureExtractorReference = ClassReference(
            moduleName = "ml_lib.feature_extractors.backbones",
            className = "SqueezeNextReduced",
            packageName = "deepcortex-ml-lib",
            packageVersion = None
          )
        ),
        description = Some("test model"),
        classNames = Some(Seq("something"))
      )
    )
  ).foreach { test =>
    "CVModelExportMeta" should {
      s"be deserializable from version '${ test.version }'" in {
        Json.parse(test.json).as[CVModelExportMeta] shouldBe test.expectedMeta
      }
    }
  }

  "CVModelExportMeta" should {

    "be serializable to current version" in {
      val testCases = Table(
        ("meta", "expectedJson"),
        (
          CVModelExportMeta(
            name = Some("name"),
            modelType = CVModelType.TL(
              consumer = CVModelType.TLConsumer.Decoder(
                classReference = ClassReference(
                  moduleName = "ml_lib.models.autoencoder.scae_model",
                  className = "SCAEModel",
                  packageName = "deepcortex-ml-lib",
                  packageVersion = None
                )
              ),
              featureExtractorReference = ClassReference(
                moduleName = "ml_lib.feature_extractors.backbones",
                className = "StackedAutoEncoder",
                packageName = "deepcortex-ml-lib",
                packageVersion = None
              )
            ),
            description = Some("description"),
            classNames = None
          ),
          Json.obj(
            "name" -> "name",
            "modelType" -> Json.obj(
              "__type" -> "TL",
              "consumer" -> Json.obj(
                "tlType" -> "DECODER",
                "classReference" -> Json.obj(
                  "moduleName" -> "ml_lib.models.autoencoder.scae_model",
                  "className" -> "SCAEModel",
                  "packageName" -> "deepcortex-ml-lib"
                )
              ),
              "featureExtractorReference" -> Json.obj(
                "moduleName" -> "ml_lib.feature_extractors.backbones",
                "className" -> "StackedAutoEncoder",
                "packageName" -> "deepcortex-ml-lib"
              )
            ),
            "description" -> "description",
            "__version" -> "2"
          )
        ),
        (
          CVModelExportMeta(
            name = Some("name"),
            modelType = CVModelType.Custom(
              classReference = ClassReference(
                moduleName = "custom.model",
                className = "CustomModel",
                packageName = "custom-models-lib",
                packageVersion = Some(Version(999, 999, 999, Some("post304+ga85b338")))
              ),
              labelMode = Some(AlbumLabelMode.Localization)
            ),
            description = Some("description"),
            classNames = Some(Seq("something"))
          ),
          Json.obj(
            "name" -> "name",
            "modelType" -> Json.obj(
              "__type" -> "CUSTOM",
              "classReference" -> Json.obj(
                "moduleName" -> "custom.model",
                "className" -> "CustomModel",
                "packageName" -> "custom-models-lib",
                "packageVersion" -> "999.999.999.post304+ga85b338"
              ),
              "labelMode" -> "LOCALIZATION"
            ),
            "description" -> "description",
            "classNames" -> Json.arr("something"),
            "__version" -> "2"
          )
        )
      )

      forAll(testCases) { (meta, expectedJson) =>
        Json.toJsObject(meta) shouldBe expectedJson
      }
    }

  }

}
