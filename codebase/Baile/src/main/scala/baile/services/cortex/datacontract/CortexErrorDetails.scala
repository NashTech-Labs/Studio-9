package baile.services.cortex.datacontract

import play.api.libs.json.{ Json, Reads }

case class CortexErrorDetails(errorCode: String, errorMessages: String, errorDetails: Map[String, String])

object CortexErrorDetails {

  implicit val CortexErrorDetailsReads: Reads[CortexErrorDetails] = Json.reads[CortexErrorDetails]

}
