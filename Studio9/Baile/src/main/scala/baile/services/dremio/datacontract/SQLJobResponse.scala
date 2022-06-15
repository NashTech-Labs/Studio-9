package baile.services.dremio.datacontract

import play.api.libs.json.{ Json, Reads }

case class SQLJobResponse(
  jobState: SQLJobStatusResponse,
  errorMessage: Option[String]
)

object SQLJobResponse {

  implicit val SQLJobResponseReads: Reads[SQLJobResponse] = Json.reads[SQLJobResponse]

}
