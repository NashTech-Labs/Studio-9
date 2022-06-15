package baile.services.tabular.model.util.export.format.v1

case class ClassReference(
  moduleName: String,
  className: String,
  packageName: String,
  packageVersion: Option[Version]
)

