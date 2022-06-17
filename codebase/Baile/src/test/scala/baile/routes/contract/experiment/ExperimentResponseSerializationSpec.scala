package baile.routes.contract.experiment

import java.time.Instant

import baile.ExtendedBaseSpec
import baile.domain.experiment.ExperimentStatus
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json.Json

class ExperimentResponseSerializationSpec extends ExtendedBaseSpec with TableDrivenPropertyChecks {

  "ExperimentResponse writes" should {
    "serialize an object properly" in {
      val instant = Instant.parse("2019-03-27T01:26:32.426Z")

      val table = Table(
        ("json", "ExperimentResponse"),
        (
          """
            |{
            |  "id":"id",
            |  "ownerId":"id",
            |  "name":"name",
            |  "created":"2019-03-27T01:26:32.426Z",
            |  "updated":"2019-03-27T01:26:32.426Z",
            |  "status":"COMPLETED",
            |  "type":"CVTLTrain"
            |}
          """.stripMargin,
          ExperimentResponse(
          id = "id",
          ownerId = "id",
          name = "name",
          created = instant,
          updated = instant,
          status = ExperimentStatus.Completed,
          description = None,
          `type` = ExperimentType.CVTLTrain
          )
        ),
        (
          """
            |{
            |  "id":"id",
            |  "ownerId":"id",
            |  "name":"name",
            |  "created":"2019-03-27T01:26:32.426Z",
            |  "updated":"2019-03-27T01:26:32.426Z",
            |  "status":"COMPLETED",
            |  "type":"GenericExperiment"
            |}
          """.stripMargin,
          ExperimentResponse(
            id = "id",
            ownerId = "id",
            name = "name",
            created = instant,
            updated = instant,
            status = ExperimentStatus.Completed,
            description = None,
            `type` = ExperimentType.GenericExperiment
          )
        ),
        (
          """
            |{
            |  "id":"id",
            |  "ownerId":"id",
            |  "name":"name",
            |  "created":"2019-03-27T01:26:32.426Z",
            |  "updated":"2019-03-27T01:26:32.426Z",
            |  "status":"COMPLETED",
            |  "type":"TabularTrain"
            |}
          """.stripMargin,
          ExperimentResponse(
            id = "id",
            ownerId = "id",
            name = "name",
            created = instant,
            updated = instant,
            status = ExperimentStatus.Completed,
            description = None,
            `type` = ExperimentType.TabularTrain
          )
        )
      )

      forAll(table) { (json, experimentResponse) =>
        val expectedJson = Json.parse(json)
        Json.toJson(experimentResponse) shouldBe expectedJson
      }
    }
  }
}

