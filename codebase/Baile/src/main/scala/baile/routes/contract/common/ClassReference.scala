package baile.routes.contract.common

import baile.domain.common.{ ClassReference => DomainClassReference }
import play.api.libs.json._

case class ClassReference(
  packageId: String,
  moduleName: String,
  className: String
) {

  def toDomain: DomainClassReference =
    DomainClassReference(
      packageId = packageId,
      moduleName = moduleName,
      className = className
    )

}

object ClassReference {

  def fromDomain(classReference: DomainClassReference): ClassReference =
    ClassReference(
      packageId = classReference.packageId,
      moduleName = classReference.moduleName,
      className = classReference.className
    )

  implicit val ClassReferenceFormat: OFormat[ClassReference] = Json.format[ClassReference]

}
