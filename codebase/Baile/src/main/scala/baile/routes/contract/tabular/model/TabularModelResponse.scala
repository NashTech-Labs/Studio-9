package baile.routes.contract.tabular.model

import java.time.Instant
import java.util.UUID

import baile.daocommons.WithId
import baile.domain.table.ColumnVariableType
import baile.domain.tabular.model.{ TabularModel, TabularModelStatus }
import baile.routes.contract.common.ClassReference
import play.api.libs.json.{ Json, OWrites }

case class TabularModelResponse(
  id: String,
  ownerId: UUID,
  name: String,
  description: Option[String],
  status: TabularModelStatus,
  created: Instant,
  updated: Instant,
  `class`: TabularModelClassResponse,
  classReference: ClassReference,
  classes: Option[Seq[String]],
  responseColumn: ModelColumn,
  predictorColumns: Seq[ModelColumn],
  inLibrary: Boolean
)

object TabularModelResponse {

  implicit val TabularModelResponseWrites: OWrites[TabularModelResponse] = Json.writes[TabularModelResponse]

  def fromDomain(model: WithId[TabularModel]): TabularModelResponse = {

    val entity = model.entity

    TabularModelResponse(
      id = model.id,
      ownerId = entity.ownerId,
      name = entity.name,
      description = entity.description,
      status = entity.status,
      created = entity.created,
      updated = entity.updated,
      `class` = entity.responseColumn.variableType match { // TODO: maybe we don't need this?
        case ColumnVariableType.Continuous => TabularModelClassResponse.Regression
        case ColumnVariableType.Categorical =>
          if (entity.classNames.fold(0)(_.length) > 2) TabularModelClassResponse.Classification
          else TabularModelClassResponse.BinaryClassification
      },
      classReference = ClassReference.fromDomain(entity.classReference),
      classes = entity.classNames,
      responseColumn = ModelColumn.fromDomain(entity.responseColumn),
      predictorColumns = entity.predictorColumns.map(ModelColumn.fromDomain),
      inLibrary = entity.inLibrary
    )
  }

}
