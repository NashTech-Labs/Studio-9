package cortex.api.job.message

import play.api.libs.json._

trait JobMessagePayload {
  val payloadType: String
}

// noinspection ScalaStyle
object JobMessagePayload {

  implicit val format: OFormat[JobMessagePayload] = new OFormat[JobMessagePayload] {

    override def writes(jobMessagePayload: JobMessagePayload): JsObject = {
      val childJson = jobMessagePayload match {
        case heartbeat: Heartbeat => Heartbeat.format.writes(heartbeat)
        case jobStarted: JobStarted => JobStarted.format.writes(jobStarted)
        case submitJob: SubmitJob => SubmitJob.format.writes(submitJob)
        case CancelJob => CancelJob.format.writes(CancelJob)
        case EmptyPayload => EmptyPayload.format.writes(EmptyPayload)
        case CleanUpResources => CleanUpResources.format.writes(CleanUpResources)
        case GetJobStatus => GetJobStatus.format.writes(GetJobStatus)
        case JobMasterAppReadyForTermination => JobMasterAppReadyForTermination.format.writes(
          JobMasterAppReadyForTermination
        )
        case jobResultSuccess: JobResultSuccess => JobResultSuccess.format.writes(jobResultSuccess)
        case jobResultFailure: JobResultFailure => JobResultFailure.format.writes(jobResultFailure)
        case unknown => throw new RuntimeException(
          s"Job message payload $unknown is not supported for json serialization"
        )
      }
      childJson + ("type" -> JsString(jobMessagePayload.payloadType))
    }

    override def reads(json: JsValue): JsResult[JobMessagePayload] = {
      json \ "type" match {
        case JsDefined(payloadType: JsString) => readByType(payloadType, json)
        case JsDefined(_) => JsError("Expected json string for type field")
        case jsUndefined: JsUndefined => JsError(jsUndefined.error)
      }
    }

    def readByType(payloadType: JsString, json: JsValue): JsResult[JobMessagePayload] = {
      payloadType.value match {
        case "EMPTY_PAYLOAD" => EmptyPayload.format.reads(json)
        case "CANCEL_JOB_PAYLOAD" => CancelJob.format.reads(json)
        case "HEARTBEAT_PAYLOAD" => Heartbeat.format.reads(json)
        case "JOB_STARTED_PAYLOAD" => JobStarted.format.reads(json)
        case "SUBMIT_JOB_PAYLOAD" => SubmitJob.format.reads(json)
        case "CLEAN_UP_RESOURCES_PAYLOAD" => CleanUpResources.format.reads(json)
        case "GET_JOB_STATUS_PAYLOAD" => GetJobStatus.format.reads(json)
        case "JOB_MASTER_APP_READY_FOR_TERMINATION_PAYLOAD" => JobMasterAppReadyForTermination.format.reads(json)
        case "JOB_RESULT_SUCCESS_PAYLOAD" => JobResultSuccess.format.reads(json)
        case "JOB_RESULT_FAILURE_PAYLOAD" => JobResultFailure.format.reads(json)
        case unknown => JsError(s"Invalid job message payload type: $unknown")
      }
    }

  }

}
