package baile.routes.contract.model

import baile.BaseSpec
import baile.domain.images.AlbumLabelMode
import baile.routes.contract.common.ClassReference
import baile.routes.contract.cv.model._
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json.{ JsSuccess, Json }

class CVModelTypeSerializationTest extends BaseSpec with TableDrivenPropertyChecks {

  "CVModelType format" should {

    "serialize and deserialize an object properly" in {
      val testCases = Table(
        ("json", "cvModelType"),
        (
          """
            |{
            |  "type": "TL",
            |  "tlType": "CLASSIFICATION",
            |  "labelMode": "CLASSIFICATION",
            |  "classifierType": "some classifier",
            |  "architecture":"some arch"
            |}
          """.stripMargin,
          CVModelType.TL(CVModelType.TLConsumer.Classifier("some classifier"), "some arch")
        ),
        (
          """
            |{
            |  "type": "TL",
            |  "tlType": "LOCALIZATION",
            |  "labelMode": "LOCALIZATION",
            |  "detectorType": "some detector",
            |  "architecture":"some arch"
            |}
          """.stripMargin,
          CVModelType.TL(CVModelType.TLConsumer.Detector("some detector"), "some arch")
        ),
        (
          """
            |{
            |  "type": "TL",
            |  "tlType": "AUTOENCODER",
            |  "labelMode": null,
            |  "decoderType": "some decoder",
            |  "architecture": "some arch"
            |}
          """.stripMargin,
          CVModelType.TL(CVModelType.TLConsumer.Decoder("some decoder"), "some arch")
        ),
        (
          """
            |{
            |  "type": "CUSTOM",
            |  "labelMode": "CLASSIFICATION",
            |  "classReference": {
            |    "packageId": "package123",
            |    "moduleName": "some module",
            |    "className": "some class"
            |  }
            |}
          """.stripMargin,
          CVModelType.Custom(
            classReference = ClassReference(
              packageId = "package123",
              moduleName = "some module",
              className = "some class"
            ),
            labelMode = Some(AlbumLabelMode.Classification)
          )
        )
      )

      forAll(testCases) { (json, cvModelType: CVModelType) =>
        val parsedJson = Json.parse(json)
        Json.fromJson[CVModelType](parsedJson) shouldBe JsSuccess(cvModelType)
        Json.toJson[CVModelType](cvModelType) shouldBe parsedJson
      }
    }

  }

}
