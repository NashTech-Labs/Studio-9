package sqlserver.utils

object StringExtensions {

  implicit class StringOps(s: String) {

    def toOption: Option[String] = Some(s).filter(_.nonEmpty)

  }

}
