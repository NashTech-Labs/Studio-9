package baile.routes.contract.onlinejob

import baile.routes.contract.onlinejob.OnlineCVPredictionOptionsResponse.OnlineCVPredictionOptionsResponseWrites
import play.api.libs.json.{ JsObject, OWrites }
import baile.routes.contract.onlinejob.OnlineJobOptionsType.OnlineJobOptionsTypeFormat

trait OnlineJobOptionsResponse {
  val `type`: OnlineJobOptionsType
}

object OnlineJobOptionsResponse {

  implicit val OnlineJobOptionsResponseWrites: OWrites[OnlineJobOptionsResponse] =
    new OWrites[OnlineJobOptionsResponse] {
      override def writes(response: OnlineJobOptionsResponse): JsObject = {
        val childJSON = response match {
          case data: OnlineCVPredictionOptionsResponse => OnlineCVPredictionOptionsResponseWrites.writes(data)
          case _ => throw new IllegalArgumentException(
            s"OnlineJobOptionsResponse serializer can't serialize ${ response.getClass }"
          )
        }

        childJSON + ("type" -> OnlineJobOptionsTypeFormat.writes(response.`type`))
      }
    }

}
