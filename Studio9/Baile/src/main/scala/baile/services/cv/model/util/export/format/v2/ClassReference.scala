package baile.services.cv.model.util.export.format.v2

case class ClassReference(
  className: String,
  moduleName: String,
  packageName: String,
  packageVersion: Option[Version]
)
