package baile.domain.common

case class Version(major: Int, minor: Int, patch: Int, suffix: Option[String]) {

  override def toString: String = s"$major.$minor.$patch" + suffix.fold("")("." + _)

}

object Version {

  implicit object VersionOrdering extends Ordering[Version] {

    override def compare(x: Version, y: Version): Int = {
      val majorC = x.major.compareTo(y.major)
      if (majorC == 0) {
        val minorC = x.minor.compareTo(y.minor)
        if (minorC == 0) {
          val patchC = x.patch.compareTo(y.patch)
          if (patchC == 0) {
            x.suffix.getOrElse("").compareTo(y.suffix.getOrElse(""))
          } else {
            patchC
          }
        } else {
          minorC
        }
      } else majorC
    }
  }

  def parseFrom(str: String): Option[Version] = {
    val regex = """^([0-9]+)\.([0-9]+)\.([0-9]+)(?:\.(.+))?$""".r
    str match {
      case regex(major, minor, patch, suffix) => Some(Version(major.toInt, minor.toInt, patch.toInt, Option(suffix)))
      case _ => None
    }
  }

}
