package cortex.api.job.message

import play.api.libs.json._

trait JobResult extends JobMessagePayload

object JobResult {

  implicit val format: OFormat[JobResult] = new OFormat[JobResult] {

    override def writes(jobResult: JobResult): JsObject = JobMessagePayload.format.writes(jobResult)

    override def reads(json: JsValue): JsResult[JobResult] =
      for {
        parentResult <- JobMessagePayload.format.reads(json)
        result <- parentResult match {
          case jobResult: JobResult => JsSuccess(jobResult)
          case unknown => JsError(s"$unknown is not job result")
        }
      } yield result

  }

}
