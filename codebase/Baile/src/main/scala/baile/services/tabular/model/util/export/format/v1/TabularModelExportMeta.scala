package baile.services.tabular.model.util.export.format.v1

import baile.services.tabular.model.util.export
import baile.utils.json.EnumFormatBuilder
import play.api.libs.json._
import play.api.libs.functional.syntax._
// FIXME Should not be here. Instead of domain this class should work with baile.services.tabular.model.util.export only
import baile.domain
import cats.implicits._

private[export] case class TabularModelExportMeta(
  name: Option[String],
  classReference: ClassReference,
  classNames: Option[Seq[String]],
  responseColumn: ModelColumn,
  predictorColumns: Seq[ModelColumn],
  description: Option[String]
) {

  def toDomain: Either[String, export.TabularModelExportMeta] = {

    def convertColumnDataType(columnDataType: ColumnDataType): domain.table.ColumnDataType = columnDataType match {
      case ColumnDataType.Integer => domain.table.ColumnDataType.Integer
      case ColumnDataType.String => domain.table.ColumnDataType.String
      case ColumnDataType.Boolean => domain.table.ColumnDataType.Boolean
      case ColumnDataType.Double => domain.table.ColumnDataType.Double
      case ColumnDataType.Long => domain.table.ColumnDataType.Long
      case ColumnDataType.Timestamp => domain.table.ColumnDataType.Timestamp
    }

    def convertColumnVariableType(columnVariableType: ColumnVariableType): domain.table.ColumnVariableType =
      columnVariableType match {
        case ColumnVariableType.Continuous => domain.table.ColumnVariableType.Continuous
        case ColumnVariableType.Categorical => domain.table.ColumnVariableType.Categorical
      }

    def convertModelColumn(modelColumn: ModelColumn): domain.tabular.model.ModelColumn = {
      domain.tabular.model.ModelColumn(
        name = modelColumn.name,
        displayName = modelColumn.displayName,
        dataType = convertColumnDataType(modelColumn.dataType),
        variableType = convertColumnVariableType(modelColumn.variableType)
      )
    }

    def convertClassReference(): export.TabularModelExportMeta.ClassReference = {
      export.TabularModelExportMeta.ClassReference(
        moduleName = classReference.moduleName,
        className = classReference.className,
        packageName = classReference.packageName,
        packageVersion = classReference.packageVersion.map { version =>
          export.TabularModelExportMeta.Version(version.major, version.minor, version.patch, version.suffix)
        }
      )
    }

    export.TabularModelExportMeta(
      name = name,
      classReference = convertClassReference(),
      classNames = classNames,
      responseColumn = convertModelColumn(responseColumn),
      predictorColumns = predictorColumns.map(convertModelColumn),
      description = description
    ).asRight

  }

}

private[export] object TabularModelExportMeta {

  implicit val VersionFormat: Format[Version] = {
    val reads = for {
      str <- Reads.of[String]
      regex = """^([0-9]+)\.([0-9]+)\.([0-9]+)(?:\.(.+))?$""".r
      result <- str match {
        case regex(major, minor, patch, suffix) =>
          Reads.pure(Version(major.toInt, minor.toInt, patch.toInt, Option(suffix)))
        case _ => Reads[Version](_ => JsError("Invalid version format"))
      }
    } yield result

    val writes = Writes.of[String].contramap[Version] { case Version(major, minor, patch, suffix) =>
      s"$major.$minor.$patch" + suffix.fold("")("." + _)
    }
    Format(reads, writes)
  }

  implicit val ColumnDataTypeFormat: Format[ColumnDataType] = EnumFormatBuilder.build(
    {
      case "STRING" => ColumnDataType.String
      case "BOOLEAN" => ColumnDataType.Boolean
      case "INTEGER" => ColumnDataType.Integer
      case "DOUBLE" => ColumnDataType.Double
      case "LONG" => ColumnDataType.Long
      case "TIMESTAMP" => ColumnDataType.Timestamp
    },
    {
      case ColumnDataType.String => "STRING"
      case ColumnDataType.Boolean => "BOOLEAN"
      case ColumnDataType.Integer => "INTEGER"
      case ColumnDataType.Double => "DOUBLE"
      case ColumnDataType.Long => "DOUBLE"
      case ColumnDataType.Timestamp => "TIMESTAMP"
    },
    "column data type"
  )

  implicit val ColumnVariableTypeFormat: Format[ColumnVariableType] = EnumFormatBuilder.build(
    {
      case "CATEGORICAL" => ColumnVariableType.Categorical
      case "CONTINUOUS" => ColumnVariableType.Continuous
    },
    {
      case ColumnVariableType.Categorical => "CATEGORICAL"
      case ColumnVariableType.Continuous => "CONTINUOUS"
    },
    "column variable type"
  )

  implicit val ClassReferenceJsonFormat: OFormat[ClassReference] = Json.format[ClassReference]

  implicit val ModelColumnJsonFormat: OFormat[ModelColumn] = Json.format[ModelColumn]

  implicit val TabularModelExportJsonFormat: OFormat[TabularModelExportMeta] = Json.format[TabularModelExportMeta]

}
