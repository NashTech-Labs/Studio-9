package baile.services.process

import play.api.libs.json.{ JsObject, JsPath, Json, JsonValidationError }

case class MetaParsingException(
  rawMeta: JsObject,
  errors: Seq[(JsPath, Seq[JsonValidationError])]
) extends RuntimeException({
  val jsErrors = errors.map { case (path, errors) =>
    s"$path: ${ errors.mkString(",") }"
  }
  s"Could not compose process result handling meta. Value: ${ Json.prettyPrint(rawMeta) }; errors: $jsErrors"
})
