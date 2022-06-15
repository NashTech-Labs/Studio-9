package baile.routes.contract.cv.model

import java.time.Instant

import baile.BaseSpec
import baile.domain.cv.model.CVModelStatus
import play.api.libs.json.Json

class CVModelResponseSerializationTest extends BaseSpec {

  "CVModelResponse writes" should {
    "serialize an object properly" in {
      val instant = Instant.parse("2019-03-27T01:26:32.426Z")
      val expectedJson = Json.parse(
        """
          |{
          |  "id":"id",
          |  "ownerId":"id",
          |  "name":"name",
          |  "created":"2019-03-27T01:26:32.426Z",
          |  "updated":"2019-03-27T01:26:32.426Z",
          |  "status":"ACTIVE",
          |  "modelType": {
          |     "type": "TL",
          |     "tlType": "CLASSIFICATION",
          |     "classifierType": "FCN_2",
          |     "architecture": "SCAE",
          |     "labelMode": "CLASSIFICATION"
          |  },
          |  "classes": ["foo"],
          |  "inLibrary":true
          |}
        """.stripMargin)

      val cvModelResponse = CVModelResponse(
        id = "id",
        ownerId = "id",
        name = "name",
        created = instant,
        updated = instant,
        status = CVModelStatus.Active,
        modelType = CVModelType.TL(CVModelType.TLConsumer.Classifier("FCN_2"), "SCAE"),
        classes = Some(Seq("foo")),
        featureExtractorModelId = None,
        description = None,
        experimentId = None,
        inLibrary = true
      )

      Json.toJson(cvModelResponse) shouldBe expectedJson
    }
  }
}
