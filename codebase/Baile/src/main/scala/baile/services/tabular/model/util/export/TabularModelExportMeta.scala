package baile.services.tabular.model.util.export

import baile.daocommons.WithId
import baile.domain.dcproject.DCProjectPackage
import baile.domain.table.{ ColumnDataType, ColumnVariableType }
import baile.domain.tabular.model.{ ModelColumn, TabularModel }
import baile.services.tabular.model.util.export.TabularModelExportMeta.{ ClassReference, Version }
import baile.services.tabular.model.util.export.format.v1
import play.api.libs.functional.syntax._
import play.api.libs.json._

// FIXME Structure should not depend on domain definitions. Refactor ModelColumn fields.
// FIXME See CVModelExportMeta as an example of correct implementation.
private[tabular] case class TabularModelExportMeta(
  name: Option[String],
  classReference: ClassReference,
  classNames: Option[Seq[String]],
  responseColumn: ModelColumn,
  predictorColumns: Seq[ModelColumn],
  description: Option[String]
) {

  def toContract: v1.TabularModelExportMeta = {

    def convertColumnDataType(columnDataType: ColumnDataType): v1.ColumnDataType = columnDataType match {
      case ColumnDataType.Integer => v1.ColumnDataType.Integer
      case ColumnDataType.String => v1.ColumnDataType.String
      case ColumnDataType.Boolean => v1.ColumnDataType.Boolean
      case ColumnDataType.Double => v1.ColumnDataType.Double
      case ColumnDataType.Long => v1.ColumnDataType.Long
      case ColumnDataType.Timestamp => v1.ColumnDataType.Timestamp
    }

    def convertColumnVariableType(columnVariableType: ColumnVariableType): v1.ColumnVariableType =
      columnVariableType match {
        case ColumnVariableType.Continuous => v1.ColumnVariableType.Continuous
        case ColumnVariableType.Categorical => v1.ColumnVariableType.Categorical
      }

    def convertClassReference(classReference: ClassReference): v1.ClassReference = {
      v1.ClassReference(
        moduleName = classReference.moduleName,
        className = classReference.className,
        packageName = classReference.packageName,
        packageVersion = classReference.packageVersion.map { case Version(major, minor, patch, suffix) =>
          v1.Version(major, minor, patch, suffix)
        }
      )
    }

    def convertModelColumn(modelColumn: ModelColumn): v1.ModelColumn = {
      v1.ModelColumn(
        name = modelColumn.name,
        displayName = modelColumn.displayName,
        dataType = convertColumnDataType(modelColumn.dataType),
        variableType = convertColumnVariableType(modelColumn.variableType)
      )
    }

    v1.TabularModelExportMeta(
      name = name,
      predictorColumns = predictorColumns.map(convertModelColumn),
      classReference = convertClassReference(classReference),
      classNames = classNames,
      responseColumn = convertModelColumn(responseColumn),
      description = description
    )
  }
}

object TabularModelExportMeta {

  implicit val TabularModelExportMetaFormat: Format[TabularModelExportMeta] = Format(
    (__ \ "__version").read[String].flatMap[TabularModelExportMeta] {
      case "1" => v1.TabularModelExportMeta.TabularModelExportJsonFormat.flatMap(_.toDomain match {
        case Right(meta) => Reads.pure(meta)
        case Left(error) => Reads(_ => JsError(s"Unknown meta version: $error"))
      })
      case unknown => Reads(_ => JsError(s"Unknown meta version: $unknown"))
    },
    v1.TabularModelExportMeta.TabularModelExportJsonFormat
      .asInstanceOf[OWrites[v1.TabularModelExportMeta]].contramap[TabularModelExportMeta](_.toContract)
  )

  def apply(model: TabularModel, modelPackage: WithId[DCProjectPackage]): TabularModelExportMeta = {
    assert(model.classReference.packageId == modelPackage.id)
    TabularModelExportMeta(
      name = Some(model.name),
      classReference = ClassReference(
        moduleName = model.classReference.moduleName,
        className = model.classReference.className,
        packageName = modelPackage.entity.name,
        packageVersion = modelPackage.entity.version.map { version =>
          Version(version.major, version.minor, version.patch, version.suffix)
        }
      ),
      classNames = model.classNames,
      responseColumn = model.responseColumn,
      predictorColumns = model.predictorColumns,
      description = model.description
    )
  }

  private[tabular] case class Version(major: Int, minor: Int, patch: Int, suffix: Option[String])

  private[tabular] case class ClassReference(
    moduleName: String,
    className: String,
    packageName: String,
    packageVersion: Option[Version]
  )

}
