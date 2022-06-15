package gemini.utils

object ThrowableExtensions {

  implicit class ThrowableOps(throwable: Throwable) {

    def printInfo: String =
      s"""${throwable.getClass.getCanonicalName}:
    ${throwable.getMessage}
    ${throwable.getStackTrace.mkString("\n")}"""

  }

}
