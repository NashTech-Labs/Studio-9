package baile.routes.contract.tabular.model

import baile.domain.table.{ ColumnDataType, ColumnVariableType }
import baile.domain.tabular.model.{ ModelColumn => DomainModelColumn }
import baile.routes.contract.table._
import play.api.libs.json.{ Json, OFormat }

case class ModelColumn(
  name: String,
  displayName: String,
  dataType: ColumnDataType,
  variableType: ColumnVariableType
) {

  def toDomain: DomainModelColumn =
    DomainModelColumn(
      name = name,
      displayName = displayName,
      dataType = dataType,
      variableType = variableType
    )

}

object ModelColumn {

  implicit val ModelColumnFormat: OFormat[ModelColumn] = Json.format[ModelColumn]

  def fromDomain(modelColumn: DomainModelColumn): ModelColumn =
    ModelColumn(
      name = modelColumn.name,
      displayName = modelColumn.displayName,
      dataType = modelColumn.dataType,
      variableType = modelColumn.variableType
    )

}
