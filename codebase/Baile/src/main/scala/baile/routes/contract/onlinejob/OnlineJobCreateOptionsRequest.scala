package baile.routes.contract.onlinejob

import baile.routes.contract.onlinejob.OnlineCVPredictionCreateOptionsRequest._
import baile.services.onlinejob.OnlineJobCreateOptions
import play.api.libs.json.{ JsValue, _ }
import OnlineJobOptionsType.OnlineJobOptionsTypeFormat

trait OnlineJobCreateOptionsRequest {
  val `type`: OnlineJobOptionsType

  def toDomain(id: String): OnlineJobCreateOptions
}

object OnlineJobCreateOptionsRequest {

  implicit val OnlineJobCreateOptionsRequestReads: Reads[OnlineJobCreateOptionsRequest] =
    new Reads[OnlineJobCreateOptionsRequest] {

      private def readOptionsByType(jobType: JsString, json: JsValue): JsResult[OnlineJobCreateOptionsRequest] =
        OnlineJobOptionsTypeFormat.reads(jobType).flatMap {
          case OnlineJobOptionsType.OnlineCvPrediction => OnlineCVPredictionCreateOptionsRequestReads.reads(json)
        }

      override def reads(json: JsValue): JsResult[OnlineJobCreateOptionsRequest] =
        json \ "type" match {
          case JsDefined(jobTypeValue: JsString) => readOptionsByType(jobTypeValue, json)
          case JsDefined(_) => JsError("Expected json string for type field")
          case jsUndefined: JsUndefined => JsError(jsUndefined.error)
        }
    }
}
