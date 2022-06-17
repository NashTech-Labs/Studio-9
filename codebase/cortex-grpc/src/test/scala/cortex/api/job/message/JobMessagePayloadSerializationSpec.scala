package cortex.api.job.message

import java.util.Date

import org.scalatest.{ FreeSpec, Matchers }
import play.api.libs.json.{ Format, Json }

import scala.concurrent.duration._

class JobMessagePayloadSerializationSpec extends FreeSpec with Matchers {

  "JobMessagePayload serialization" - {

    def checkSerialization[P: Format](payload: P) {
      val json = Json.toJson(payload)
      val restoredPayload = Json.fromJson[P](json)
      restoredPayload.get shouldBe payload
    }

    List[JobMessagePayload](
      CancelJob,
      CleanUpResources,
      EmptyPayload,
      GetJobStatus,
      Heartbeat(new Date(), 0.42D, Some(20.seconds)),
      JobMasterAppReadyForTermination,
      JobResultFailure(new Date(), "error-code", "error 42", Map("stackTrace" -> "error")),
      JobResultSuccess(new Date(), Seq.empty[TaskTimeInfo], 1 second, "output/path"),
      JobStarted(new Date()),
      SubmitJob("input/path")
    ).foreach { payload =>
      payload.getClass.getName in {
        checkSerialization(payload)
      }
    }

  }


}
