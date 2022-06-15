package baile.routes.contract.experiment

import baile.ExtendedBaseSpec
import baile.domain.experiment.ExperimentStatus
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.libs.json.{ JsString, Json }

class ExperimentStatusSerializationSpec extends ExtendedBaseSpec with TableDrivenPropertyChecks {

  "ExperimentStatus writes" should {
    "serialize an object properly" in {
      val table = Table(
        ("json", "ExperimentStatus"),
        ("COMPLETED", ExperimentStatus.Completed),
        ("CANCELLED", ExperimentStatus.Cancelled),
        ("ERROR", ExperimentStatus.Error),
        ("RUNNING", ExperimentStatus.Running)
      )

      forAll(table) { (json, experimentStatus) =>
        Json.toJson(experimentStatus) shouldBe JsString(json)
      }
    }
  }
}

