package baile.routes.contract.common

import baile.domain.common.{ Version => DomainVersion }
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class Version(major: Int, minor: Int, patch: Int, suffix: Option[String]) {

  def toDomain: DomainVersion = DomainVersion(major, minor, patch, suffix)

}

object Version {

  implicit val VersionFormat: Format[Version] = {
    val reads = for {
      str <- Reads.of[String]
      result <- DomainVersion.parseFrom(str) match {
        case Some(DomainVersion(major, minor, patch, suffix)) =>
          Reads.pure[Version](Version(major, minor, patch, suffix))
        case None => Reads[Version](_ => JsError("Invalid version format"))
      }
    } yield result
    val writes = Writes.of[String].contramap[Version](_.toDomain.toString)
    Format(reads, writes)
  }

  def fromDomain(version: DomainVersion): Version =
    Version(version.major, version.minor, version.patch, version.suffix)

}
