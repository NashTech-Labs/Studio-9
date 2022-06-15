package baile.routes.contract.tabular.prediction

import java.time.Instant

import baile.daocommons.WithId
import baile.domain.tabular.prediction.{ TabularPrediction, TabularPredictionStatus }
import play.api.libs.json.{ Json, OWrites }
import baile.routes.contract.tabular.TabularPredictionStatusWrites

case class TabularPredictionResponse(
  id: String,
  ownerId: String,
  name: String,
  status: TabularPredictionStatus,
  created: Instant,
  updated: Instant,
  modelId: String,
  input: Seq[String],
  output: String,
  columnMappings: Seq[SimpleMappingPair],
  description: Option[String]
)

object TabularPredictionResponse {

  implicit val TabularPredictionResponseWrites: OWrites[TabularPredictionResponse] =
    Json.writes[TabularPredictionResponse]

  def fromDomain(prediction: WithId[TabularPrediction]): TabularPredictionResponse = {
    val entity = prediction.entity

    TabularPredictionResponse(
      id = prediction.id,
      ownerId = entity.ownerId.toString,
      name = entity.name,
      status = entity.status,
      created = entity.created,
      updated = entity.updated,
      modelId = entity.modelId,
      input = Seq(entity.inputTableId),
      output = entity.outputTableId,
      columnMappings = entity.columnMappings.map { pair =>
        SimpleMappingPair(
          sourceColumn = pair.trainName,
          mappedColumn = pair.currentName
        )
      },
      description = entity.description
    )
  }

}
