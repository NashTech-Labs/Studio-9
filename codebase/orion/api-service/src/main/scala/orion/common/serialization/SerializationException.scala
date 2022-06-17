package orion.common.serialization

import play.api.libs.json.{ JsPath, JsonValidationError }

class SerializationException(message: String, errors: Seq[(JsPath, Seq[JsonValidationError])]) extends RuntimeException {

  override def getMessage: String =
    s"$message\nerrors:\n${errors.map(errorToString).mkString("\t", "\n\t", "\n")}}"

  private def errorToString(t: (JsPath, Seq[JsonValidationError])) = t match {
    case (path, pathErrors) => s"$path: " + pathErrors.mkString(", ")
  }
}
