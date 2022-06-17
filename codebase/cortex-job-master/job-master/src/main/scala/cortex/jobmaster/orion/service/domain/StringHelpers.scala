package cortex.jobmaster.orion.service.domain

trait StringHelpers {
  implicit class StringExtension(str: String) {
    def removeTrailingSlashes(): String = {
      str.reverse.dropWhile(_ == '/').reverse
    }
  }
}

object StringHelpers extends StringHelpers
