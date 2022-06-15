package baile.routes.contract.common

import play.api.libs.json.{ Json, OWrites }

case class UserStatsResponse(
  tablesCount: Int,
  flowsCount: Int,
  modelsCount: Int,
  projectsCount: Int,
  cvModelsCount: Int,
  albumsCount: Int,
  binaryDatasetsCount: Int,
  pipelinesCount: Int,
  experimentsCount: Int,
  cvPredictionsCount: Int,
  tabularPredictionsCount: Int,
  dcProjectsCount: Int
)

object UserStatsResponse {
  implicit val UserStatsResponseWrites: OWrites[UserStatsResponse] = Json.writes[UserStatsResponse]
}
